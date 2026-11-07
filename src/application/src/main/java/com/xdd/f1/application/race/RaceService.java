package com.xdd.f1.application.race;

import com.xdd.f1.application.league.LeagueAccessGuard;
import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.contract.race.RaceClosedEvent;
import com.xdd.f1.contract.race.RaceOpenedEvent;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.user.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.*;

@Service
public class RaceService {
    private final RaceRepository raceRepository;
    private final RaceCalendarProvider raceCalendarProvider;
    private final LeagueAccessGuard leagueAccessGuard;
    private final LeagueRepository leagueRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RaceService(
            RaceRepository raceRepository,
            RaceCalendarProvider raceCalendarProvider,
            LeagueAccessGuard leagueAccessGuard,
            LeagueRepository leagueRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.raceRepository = raceRepository;
        this.raceCalendarProvider = raceCalendarProvider;
        this.leagueAccessGuard = leagueAccessGuard;
        this.leagueRepository = leagueRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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

    public List<Race> findSeasonCalendarForLeague(UserId userId, LeagueId leagueId, Optional<Integer> season){
        var requestedSeason = season.orElse(Year.now(clock).getValue());
        leagueAccessGuard.checkAccess(userId, leagueId);
        var league = leagueRepository.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        var calendar = raceRepository.findAllBySeason(requestedSeason);
        return calendar.stream()
                .filter(race -> league.settings().sprintsEnabled() || race.type() != RaceType.SPRINT)
                .toList();
    }

    @Transactional
    public Race create(int season, int round, RaceType type, String name, Instant openAt, Instant closeAt) {
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

    @Transactional
    public Map<RaceId, RaceStatus> processScheduledTransitions() {
        var now = clock.instant();
        var result = new HashMap<RaceId, RaceStatus>();

        raceRepository.findRacesToOpen(now)
                .forEach(race -> {
                    openRacePredictions(race.id());
                    result.put(race.id(), RaceStatus.OPEN);
                });

        raceRepository.findRacesToClose(now)
                .forEach(race -> {
                    closeRacePredictions(race.id());
                    result.put(race.id(), RaceStatus.CLOSED);
                });

        return result;
    }

    @Transactional
    public void syncCalendar(int season) {
        var currentSeason = raceRepository.findAllBySeason(season);
        var seasonEntries = raceCalendarProvider.loadSeason(season);
        seasonEntries.forEach(e -> {
            var existingRace = currentSeason.stream().filter(r ->
                    r.round() == e.round()
                            && r.type() == e.type()
            ).findFirst();
            if (existingRace.isEmpty()) {
                create(e.season(), e.round(), e.type(), e.name(), e.openAt(), e.closeAt());
            } else {
                var currentRace = existingRace.get();
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
        var race = raceRepository.findById(id).orElseThrow(() -> new RaceNotFoundException(id));
        var updatedRace = race.open();
        if (race.equals(updatedRace)) {
            return;
        }
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
        var race = raceRepository.findById(id).orElseThrow(() -> new RaceNotFoundException(id));
        var updatedRace = race.finish();
        raceRepository.update(updatedRace);
    }
}
