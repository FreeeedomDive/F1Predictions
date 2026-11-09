package com.xdd.f1.application.race;

import com.xdd.f1.application.league.LeagueAccessGuard;
import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.prediction.PredictionConditionsService;
import com.xdd.f1.application.race.lineup.RaceLineupService;
import com.xdd.f1.contract.race.RaceClosedEvent;
import com.xdd.f1.contract.race.RaceOpenedEvent;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.user.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.*;
import java.util.stream.Stream;

@Service
public class RaceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaceService.class);

    private final RaceRepository raceRepository;
    private final LeagueAccessGuard leagueAccessGuard;
    private final LeagueRepository leagueRepository;
    private final RaceLineupService lineupService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionLock lock;
    private final PredictionConditionsService conditionsService;
    private final TransactionTemplate transitionTransactions;

    public RaceService(
            RaceRepository raceRepository,
            LeagueAccessGuard leagueAccessGuard,
            LeagueRepository leagueRepository,
            RaceLineupService lineupService,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            TransactionLock lock,
            PredictionConditionsService conditionsService,
            PlatformTransactionManager transactionManager
    ) {
        this.raceRepository = raceRepository;
        this.leagueAccessGuard = leagueAccessGuard;
        this.leagueRepository = leagueRepository;
        this.lineupService = lineupService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.lock = lock;
        this.conditionsService = conditionsService;
        this.transitionTransactions = new TransactionTemplate(transactionManager);
        this.transitionTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public Race getById(RaceId id) {
        return raceRepository.findById(id).orElseThrow(() -> new RaceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Race> findAllBySeason(int season) {
        return raceRepository.findAllBySeason(season);
    }

    @Transactional(readOnly = true)
    public List<Race> findFinishedBySeason(int season) {
        return raceRepository.findFinishedBySeason(season);
    }

    public List<Race> findSeasonCalendarForLeague(UserId userId, LeagueId leagueId, Optional<Integer> season) {
        var requestedSeason = season.orElse(Year.now(clock).getValue());
        leagueAccessGuard.checkAccess(userId, leagueId);
        var league = leagueRepository.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        if (requestedSeason < league.settings().firstSeason()) {
            return List.of();
        }
        var calendar = raceRepository.findAllBySeason(requestedSeason);
        return calendar.stream()
                .filter(race -> league.settings().sprintsEnabled() || race.type() != RaceType.SPRINT)
                .toList();
    }

    @Transactional
    public Race create(int season, int round, RaceType type, String name, Instant openAt, Instant closeAt) {
        return createRace(season, round, type, name, openAt, closeAt);
    }

    private Race createRace(int season, int round, RaceType type, String name, Instant openAt, Instant closeAt) {
        var race = new Race(
                new RaceId(UUID.randomUUID()),
                season,
                round,
                type,
                name,
                RaceStatus.SCHEDULED,
                openAt,
                closeAt
        );
        raceRepository.create(race);
        return race;
    }

    public Map<RaceId, RaceStatus> processScheduledTransitions() {
        var now = clock.instant();
        var result = new HashMap<RaceId, RaceStatus>();
        var toClose = raceRepository.findRacesToClose(now);
        var toOpen = raceRepository.findRacesToOpen(now);
        var candidates = Stream.concat(toClose.stream(), toOpen.stream())
                .map(Race::id).distinct()
                .toList();
        for (var raceId : candidates) {
            try {
                var updatedStatus = transitionTransactions.execute(status -> processScheduledTransition(raceId));
                if (updatedStatus != null) {
                    result.put(raceId, updatedStatus);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Could not process scheduled transitions for race {}", raceId.value(), exception);
            }
        }
        return result;
    }

    private RaceStatus processScheduledTransition(RaceId raceId) {
        lock.acquire("race:" + raceId.value());
        var race = raceRepository.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
        RaceStatus updatedStatus = null;
        if (race.status() == RaceStatus.SCHEDULED && !race.openAt().isAfter(clock.instant())) {
            openPredictions(raceId);
            race = race.open();
            updatedStatus = RaceStatus.OPEN;
        }
        if (race.status() == RaceStatus.OPEN && !race.closeAt().isAfter(clock.instant())) {
            closePredictions(raceId);
            updatedStatus = RaceStatus.CLOSED;
        }
        return updatedStatus;
    }

    @Transactional
    public void applyCalendar(int season, List<RaceCalendarEntry> seasonEntries) {
        var currentSeason = raceRepository.findAllBySeason(season);
        currentSeason.stream().map(Race::id).sorted(Comparator.comparing(id -> id.value().toString()))
                .forEach(id -> lock.acquire("race:" + id.value()));
        seasonEntries.forEach(e -> {
            var existingRace = currentSeason.stream().filter(r ->
                    r.round() == e.round()
                            && r.type() == e.type()
            ).findFirst();
            if (existingRace.isEmpty()) {
                createRace(e.season(), e.round(), e.type(), e.name(), e.openAt(), e.closeAt());
            } else {
                var currentRace = raceRepository.findById(existingRace.get().id())
                        .orElseThrow(() -> new RaceNotFoundException(existingRace.get().id()));
                var updatedRace = currentRace
                        .rename(e.name())
                        .reschedule(e.openAt(), e.closeAt());
                if (!updatedRace.equals(currentRace)) {
                    raceRepository.update(updatedRace);
                }
            }
        });
    }

    @Transactional
    public void openRacePredictions(RaceId id) {
        openPredictions(id);
    }

    private void openPredictions(RaceId id) {
        lock.acquire("race:" + id.value());
        var race = raceRepository.findById(id).orElseThrow(() -> new RaceNotFoundException(id));
        var updatedRace = race.open();
        if (race.equals(updatedRace)) {
            return;
        }
        lineupService.ensureForRace(race);
        conditionsService.ensureForRace(race);
        eventPublisher.publishEvent(new RaceOpenedEvent(
                updatedRace.id().value(),
                updatedRace.name(),
                String.valueOf(updatedRace.type()),
                updatedRace.closeAt()
        ));
        raceRepository.update(updatedRace);
    }

    @Transactional
    public void closeRacePredictions(RaceId id) {
        closePredictions(id);
    }

    private void closePredictions(RaceId id) {
        lock.acquire("race:" + id.value());
        var race = raceRepository.findById(id).orElseThrow(() -> new RaceNotFoundException(id));
        var updatedRace = race.close();
        if (race.equals(updatedRace)) {
            return;
        }
        eventPublisher.publishEvent(new RaceClosedEvent(updatedRace.id().value(), updatedRace.name(), String.valueOf(updatedRace.type())));
        raceRepository.update(updatedRace);
    }

    @Transactional
    public void finishRace(RaceId id) {
        lock.acquire("race:" + id.value());
        var race = raceRepository.findById(id).orElseThrow(() -> new RaceNotFoundException(id));
        var updatedRace = race.finish();
        raceRepository.update(updatedRace);
    }
}
