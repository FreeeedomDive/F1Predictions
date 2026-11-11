package com.xdd.f1.application.prediction;

import com.xdd.f1.application.league.LeagueAccessGuard;
import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.lineup.RaceLineupNotFoundException;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.contract.prediction.PredictionChangedEvent;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.*;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.user.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;
import java.util.*;

@Service
public class PredictionService {
    private final PredictionRepository predictions;
    private final PredictionConditionsRepository conditions;
    private final RaceRepository races;
    private final RaceLineupRepository lineups;
    private final LeagueRepository leagues;
    private final LeagueParticipantRepository participants;
    private final LeagueAccessGuard access;
    private final TransactionLock lock;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PredictionService(PredictionRepository predictions, PredictionConditionsRepository conditions,
                             RaceRepository races, RaceLineupRepository lineups, LeagueRepository leagues,
                             LeagueParticipantRepository participants, LeagueAccessGuard access, TransactionLock lock,
                             ApplicationEventPublisher events, Clock clock) {
        this.predictions = predictions;
        this.conditions = conditions;
        this.races = races;
        this.lineups = lineups;
        this.leagues = leagues;
        this.participants = participants;
        this.access = access;
        this.lock = lock;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<RaceDetails> findCurrentDetails(UserId userId, LeagueId leagueId, Optional<Integer> season) {
        access.checkAccess(userId, leagueId);
        var league = leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        int requestedSeason = season.orElse(Year.now(clock).getValue());
        if (requestedSeason < league.settings().firstSeason()) {
            return Optional.empty();
        }
        var now = clock.instant();
        var calendar = races.findAllBySeason(requestedSeason).stream()
                .filter(race -> race.type() != RaceType.SPRINT || league.settings().sprintsEnabled()).toList();
        var chronological = Comparator.comparing(Race::closeAt);
        var current = calendar.stream()
                .filter(race -> race.status() == RaceStatus.OPEN && now.isBefore(race.closeAt()))
                .min(chronological)
                .or(() -> calendar.stream()
                        .filter(race -> race.status() == RaceStatus.SCHEDULED && now.isBefore(race.closeAt()))
                        .min(chronological));
        return current.map(this::toDetails);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RaceDetails getDetails(UserId userId, LeagueId leagueId, RaceId raceId) {
        access.checkAccess(userId, leagueId);
        var race = getRace(leagueId, raceId);
        return toDetails(race);
    }

    private RaceDetails toDetails(Race race) {
        var raceId = race.id();
        var saved = lineups.findByRaceId(raceId);
        var lineup = saved.or(() -> race.status() == RaceStatus.SCHEDULED
                ? lineups.findLatestBefore(race.closeAt()) : Optional.empty());
        var now = clock.instant();
        return new RaceDetails(race, lineup.map(RaceLineup::teams).orElse(List.of()), saved.isPresent(),
                conditions.findByRaceId(raceId).orElse(null),
                race.status() == RaceStatus.OPEN && now.isBefore(race.closeAt()) && saved.isPresent(), now);
    }

    @Transactional(readOnly = true)
    public Optional<Prediction> findOwn(UserId userId, LeagueId leagueId, RaceId raceId) {
        var participant = access.checkAccess(userId, leagueId);
        getRace(leagueId, raceId);
        return predictions.findByLeagueAndRaceAndParticipant(leagueId, raceId, participant.id());
    }

    @Transactional(readOnly = true)
    public List<PredictionDetails> findAllForAdmin(RaceId raceId) {
        races.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
        var saved = predictions.findAllByRaceId(raceId);
        var names = participants.findNamesByIds(saved.stream().map(Prediction::participantId).distinct().toList());
        var leagueNames = leagues.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(League::id, League::name));
        return saved.stream().map(prediction -> {
                    var participantName = names.get(prediction.participantId());
                    if (participantName == null) {
                        throw new LeagueParticipantNotFoundException(prediction.participantId());
                    }
                    var leagueName = leagueNames.get(prediction.leagueId());
                    if (leagueName == null) {
                        throw new LeagueNotFoundException(prediction.leagueId());
                    }
                    return new PredictionDetails(prediction, participantName, leagueName);
                }).sorted(Comparator.comparing(PredictionDetails::leagueName)
                        .thenComparing(PredictionDetails::participantName))
                .toList();
    }

    @Transactional
    public Prediction save(UserId userId, LeagueId leagueId, RaceId raceId, PredictionValues values) {
        lock.acquire("race:" + raceId.value());
        var race = getRace(leagueId, raceId);
        var participant = access.checkAccess(userId, leagueId);
        var previous = predictions.findByLeagueAndRaceAndParticipant(leagueId, raceId, participant.id());
        return save(race, leagueId, participant.id(), participant.displayName(), previous, values, true);
    }

    @Transactional
    public Prediction saveForAdmin(RaceId raceId, PredictionId predictionId, PredictionValues values) {
        lock.acquire("race:" + raceId.value());
        var race = races.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
        var previous = predictions.findAllByRaceId(raceId).stream()
                .filter(prediction -> prediction.id().equals(predictionId))
                .findFirst().orElseThrow(() -> new PredictionNotFoundException(predictionId));
        var participantName = participants.findNamesByIds(List.of(previous.participantId())).get(previous.participantId());
        if (participantName == null) {
            throw new LeagueParticipantNotFoundException(previous.participantId());
        }
        return save(race, previous.leagueId(), previous.participantId(), participantName, Optional.of(previous), values, false);
    }

    private Prediction save(Race race, LeagueId leagueId, LeagueParticipantId participantId, String participantName,
                            Optional<Prediction> previous, PredictionValues values, boolean requireOpen) {
        if (requireOpen) {
            checkOpen(race);
        }
        var updated = new Prediction(previous.map(Prediction::id).orElseGet(() -> new PredictionId(UUID.randomUUID())),
                leagueId, participantId, race.id(), values, previous.map(Prediction::teamsPickedDrivers).orElse(null));
        PredictionValidator.validate(race.season(), updated);
        var raceConditions = conditions.findByRaceId(race.id()).orElse(null);
        var lineup = lineups.findByRaceId(race.id()).orElseThrow(() -> new RaceLineupNotFoundException(race.id()));
        validatePredictionAgainstLineup(updated, lineup, raceConditions);
        if (requireOpen) {
            checkOpen(race);
        }
        if (previous.isPresent() && sameValues(previous.get().values(), values)) {
            return previous.get();
        }
        if (previous.isPresent()) {
            predictions.update(updated);
        } else {
            predictions.create(updated);
        }
        if (requireOpen) {
            events.publishEvent(new PredictionChangedEvent(leagueId.value(), participantId.value(), participantName,
                    race.id().value(), race.name(), race.type().name(), previous.isEmpty()));
        }
        return updated;
    }

    private Race getRace(LeagueId leagueId, RaceId raceId) {
        var race = races.findById(raceId)
                .orElseThrow(() -> new RaceNotFoundException(raceId));
        var league = leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        if (race.season() < league.settings().firstSeason()
                || race.type() == RaceType.SPRINT && !league.settings().sprintsEnabled()) {
            throw new RaceNotFoundException(raceId);
        }
        return race;
    }

    private void checkOpen(Race race) {
        if (race.status() != RaceStatus.OPEN || !clock.instant().isBefore(race.closeAt())) {
            throw new PredictionsClosedException(race.id());
        }
    }

    private void validatePredictionAgainstLineup(Prediction prediction, RaceLineup lineup, PredictionConditions raceConditions) {
        var drivers = lineup.teams().stream().flatMap(team -> team.drivers().stream()).toList();
        var values = prediction.values();
        requireDriver(values.tenthPlacePickedDriver(), drivers);
        if (values.dnfPrediction() != null) {
            values.dnfPrediction().dnfPickedDrivers().forEach(driver -> requireDriver(driver, drivers));
        }
        if (values.driverPositionPrediction() != null) {
            if (raceConditions == null || raceConditions.positionPredictionDriver() == null) {
                throw new DomainValidationException("Driver position prediction is not enabled for this race");
            }
            if (values.driverPositionPrediction() > drivers.size()) {
                throw new DomainValidationException("Driver position must not exceed the number of drivers in the race lineup");
            }
        }
        if (prediction.teamsPickedDrivers() != null) {
            var selected = new HashSet<>(prediction.teamsPickedDrivers());
            selected.forEach(name -> requireDriver(new Driver(name), drivers));
            if (selected.size() != lineup.teams().size()
                    || lineup.teams().stream().anyMatch(team -> team.drivers().stream()
                    .filter(driver -> selected.contains(driver.name())).count() != 1)) {
                throw new DomainValidationException("Team predictions must select one driver from each team in the race lineup");
            }
        }
    }

    private void requireDriver(Driver driver, List<Driver> drivers) {
        if (driver != null && !drivers.contains(driver)) {
            throw new DomainValidationException("Driver does not belong to this race lineup: " + driver.name());
        }
    }

    private boolean sameValues(PredictionValues first, PredictionValues second) {
        var firstDnf = first.dnfPrediction();
        var secondDnf = second.dnfPrediction();
        var sameDnf = Objects.equals(firstDnf, secondDnf)
                || firstDnf != null && secondDnf != null
                && new HashSet<>(firstDnf.dnfPickedDrivers()).equals(new HashSet<>(secondDnf.dnfPickedDrivers()));
        return sameDnf && Objects.equals(first.tenthPlacePickedDriver(), second.tenthPlacePickedDriver())
                && first.safetyCarsPrediction() == second.safetyCarsPrediction()
                && Objects.equals(first.driverPositionPrediction(), second.driverPositionPrediction())
                && Objects.equals(first.firstPlaceLeadPrediction(), second.firstPlaceLeadPrediction());
    }
}
