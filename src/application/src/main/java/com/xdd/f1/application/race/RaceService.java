package com.xdd.f1.application.race;

import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RaceService {
    private final RaceRepository raceRepository;
    private final Clock clock;

    public RaceService(
            RaceRepository raceRepository,
            Clock clock
    ) {
        this.raceRepository = raceRepository;
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
        raceRepository.createOrUpdate(race);
        return race;
    }

    @Transactional
    public void processScheduledTransitions() {
        var now = clock.instant();

        raceRepository.findRacesToOpen(now)
                .forEach(race -> openRacePredictions(race.id()));

        raceRepository.findRacesToClose(now)
                .forEach(race -> closeRacePredictions(race.id()));
    }

    @Transactional
    public void openRacePredictions(RaceId id) {
        var race = raceRepository.findById(id).orElseThrow(() -> new RaceNotFoundException(id));
        var updatedRace = race.open();
        raceRepository.createOrUpdate(updatedRace);
    }

    @Transactional
    public void closeRacePredictions(RaceId id) {
        var race = raceRepository.findById(id).orElseThrow(() -> new RaceNotFoundException(id));
        var updatedRace = race.close();
        raceRepository.createOrUpdate(updatedRace);
    }

    @Transactional
    public void finishRace(RaceId id) {
        var race = raceRepository.findById(id).orElseThrow(() -> new RaceNotFoundException(id));
        var updatedRace = race.finish();
        raceRepository.createOrUpdate(updatedRace);
    }
}
