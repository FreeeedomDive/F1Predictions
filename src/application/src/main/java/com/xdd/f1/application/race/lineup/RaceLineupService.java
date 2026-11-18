package com.xdd.f1.application.race.lineup;

import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.result.RaceResultService;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.prediction.PredictionRepository;
import com.xdd.f1.application.prediction.PredictionConditionsRepository;
import com.xdd.f1.contract.race.DriverReplacementDto;
import com.xdd.f1.contract.race.RaceDriversMovedEvent;
import com.xdd.f1.contract.race.RaceDriversReplacedEvent;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.DriverReplacement;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.lineup.RaceLineupChanges;
import com.xdd.f1.domain.race.lineup.RaceLineupChanges.TeamEdit;
import com.xdd.f1.domain.race.lineup.Team;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
public class RaceLineupService {
    private final RaceRepository raceRepository;
    private final RaceLineupRepository lineupRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionLock lock;
    private final PredictionRepository predictions;
    private final PredictionConditionsRepository conditions;
    private final RaceResultService results;

    public RaceLineupService(
            RaceRepository raceRepository,
            RaceLineupRepository lineupRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            TransactionLock lock,
            PredictionRepository predictions,
            PredictionConditionsRepository conditions,
            RaceResultService results
    ) {
        this.raceRepository = raceRepository;
        this.lineupRepository = lineupRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.lock = lock;
        this.predictions = predictions;
        this.conditions = conditions;
        this.results = results;
    }

    @Transactional(readOnly = true)
    public Optional<RaceLineupEditor> findTarget(int season) {
        var races = raceRepository.findAllBySeason(season);
        var chronological = Comparator.comparing(Race::closeAt).thenComparing(race -> race.id().value());
        var now = clock.instant();
        var target = races.stream()
                .filter(race -> race.status() == RaceStatus.OPEN)
                .min(chronological)
                .or(() -> races.stream()
                        .filter(race -> race.status() == RaceStatus.SCHEDULED && race.closeAt().isAfter(now))
                        .min(chronological));
        return target.map(this::toEditor);
    }

    @Transactional(readOnly = true)
    public RaceLineupEditor getForEditing(RaceId raceId) {
        return toEditor(findRace(raceId));
    }

    @Transactional
    public RaceLineupEditor save(
            RaceId raceId, List<Team> baseTeams, List<TeamEdit> edits, List<DriverReplacement> confirmedReplacements
    ) {
        lock.acquire("race:" + raceId.value());
        var race = findRace(raceId);
        var current = toEditor(race);
        var driversEditable = canEditDrivers(race);
        if (!driversEditable && !current.prepared()) {
            throw new RaceLineupNotFoundException(raceId);
        }
        if (!current.teams().equals(baseTeams)) {
            throw new RaceLineupConflictException(raceId);
        }
        if (!driversEditable) {
            validateHistoricalEdit(race, current.teams(), edits, confirmedReplacements);
        } else if (edits.stream().anyMatch(edit -> edit.team().drivers().size() > 2)) {
            throw new DomainValidationException("Team must not have more than two drivers");
        }
        var changes = RaceLineupChanges.calculate(raceId, current.teams(), edits, confirmedReplacements);
        var lineup = changes.lineup();
        if (current.prepared() && current.teams().equals(lineup.teams())) {
            return current;
        }
        if (!current.prepared()) {
            if (!lineupRepository.createIfAbsent(lineup)) {
                throw new RaceLineupConflictException(raceId);
            }
        } else {
            lineupRepository.update(lineup);
        }
        if (driversEditable) {
            if (!changes.replacements().isEmpty()) {
                replaceDriverReferences(race.id(), changes.replacements());
            }
            changes.teams().forEach(change -> publishDriverChanges(race, change));
        }
        return new RaceLineupEditor(race, lineup.teams(), true);
    }

    private void validateHistoricalEdit(
            Race race, List<Team> before, List<TeamEdit> edits, List<DriverReplacement> replacements
    ) {
        if (!replacements.isEmpty() || edits.size() != before.size()) {
            throw new RaceLineupNotEditableException(race.id(), race.status());
        }
        var driversByTeam = new HashMap<String, List<Driver>>();
        before.forEach(team -> driversByTeam.put(team.branding().name(), team.drivers()));
        for (var edit : edits) {
            if (!edit.team().drivers().equals(driversByTeam.remove(edit.previousName()))) {
                throw new RaceLineupNotEditableException(race.id(), race.status());
            }
        }
    }

    @Transactional
    public void ensureForRace(Race race) {
        lock.acquire("race:" + race.id().value());
        if (lineupRepository.findByRaceId(race.id()).isPresent()) {
            return;
        }

        var previous = lineupRepository.findLatestBefore(race.closeAt())
                .orElseThrow(() -> new RaceLineupNotPreparedException(race.id()));
        lineupRepository.createIfAbsent(new RaceLineup(race.id(), previous.teams()));
    }

    private void replaceDriverReferences(RaceId raceId, List<DriverReplacement> replacements) {
        for (var prediction : predictions.findAllByRaceId(raceId)) {
            var updated = prediction.replaceDrivers(replacements);
            if (!updated.equals(prediction)) {
                predictions.update(updated);
            }
        }
        conditions.findByRaceId(raceId).ifPresent(currentConditions -> {
            var updated = currentConditions.replaceDrivers(replacements);
            if (!updated.equals(currentConditions)) {
                conditions.update(updated);
            }
        });
        results.replaceStartingGridDrivers(raceId, replacements);
    }

    private void publishDriverChanges(Race race, RaceLineupChanges.TeamChange change) {
        var branding = change.branding();
        if (!change.replacements().isEmpty()) {
            eventPublisher.publishEvent(new RaceDriversReplacedEvent(
                    race.id().value(), race.name(), race.type().name(), branding.name(), branding.backgroundColor(),
                    change.replacements().stream()
                            .map(pair -> new DriverReplacementDto(pair.previousDriver().name(), pair.newDriver().name()))
                            .toList()
            ));
        }
        if (!change.departures().isEmpty() || !change.arrivals().isEmpty()) {
            eventPublisher.publishEvent(new RaceDriversMovedEvent(
                    race.id().value(), race.name(), race.type().name(), branding.name(), branding.backgroundColor(),
                    change.departures().stream().map(Driver::name).toList(),
                    change.arrivals().stream().map(Driver::name).toList()
            ));
        }
    }

    private Race findRace(RaceId raceId) {
        return raceRepository.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
    }

    private boolean canEditDrivers(Race race) {
        return race.status() == RaceStatus.SCHEDULED || race.status() == RaceStatus.OPEN;
    }

    private RaceLineupEditor toEditor(Race race) {
        var saved = lineupRepository.findByRaceId(race.id());
        var lineup = canEditDrivers(race) ? saved.or(() -> lineupRepository.findLatestBefore(race.closeAt())) : saved;
        var teams = lineup.map(RaceLineup::teams).orElse(List.of());
        return new RaceLineupEditor(race, teams, saved.isPresent());
    }
}
