package com.xdd.f1.application.race.lineup;

import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.contract.race.RaceDriverReplacedEvent;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class RaceLineupService {
    private final RaceRepository raceRepository;
    private final RaceLineupRepository lineupRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RaceLineupService(
            RaceRepository raceRepository,
            RaceLineupRepository lineupRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.raceRepository = raceRepository;
        this.lineupRepository = lineupRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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
        var race = raceRepository.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
        return toEditor(race);
    }

    @Transactional
    public RaceLineup save(RaceLineup lineup) {
        getEditableRace(lineup.raceId());

        if (!lineupRepository.createIfAbsent(lineup)) {
            var existing = lineupRepository.findByRaceId(lineup.raceId())
                    .orElseThrow(() -> new RaceLineupNotFoundException(lineup.raceId()));
            if (!existing.equals(lineup)) {
                lineupRepository.update(lineup);
            }
        }
        return lineup;
    }

    @Transactional
    public RaceLineup replaceDriver(RaceId raceId, Driver previousDriver, Driver newDriver) {
        var race = getEditableRace(raceId);
        ensureForRace(race);
        var current = lineupRepository.findByRaceId(raceId)
                .orElseThrow(() -> new RaceLineupNotFoundException(raceId));
        var updated = current.replaceDriver(previousDriver, newDriver);
        if (current.equals(updated)) {
            return current;
        }

        lineupRepository.update(updated);
        eventPublisher.publishEvent(new RaceDriverReplacedEvent(
                race.id().value(),
                race.name(),
                race.type().name(),
                previousDriver.name(),
                newDriver.name()
        ));
        return updated;
    }

    @Transactional
    public void ensureForRace(Race race) {
        if (lineupRepository.findByRaceId(race.id()).isPresent()) {
            return;
        }

        var previous = lineupRepository.findLatestBefore(race.closeAt())
                .orElseThrow(() -> new RaceLineupNotPreparedException(race.id()));
        lineupRepository.createIfAbsent(new RaceLineup(race.id(), previous.teams()));
    }

    private Race getEditableRace(RaceId raceId) {
        var race = raceRepository.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
        if (race.status() != RaceStatus.SCHEDULED && race.status() != RaceStatus.OPEN) {
            throw new RaceLineupNotEditableException(race.id(), race.status());
        }
        return race;
    }

    private RaceLineupEditor toEditor(Race race) {
        var saved = lineupRepository.findByRaceId(race.id());
        var teams = saved.or(() -> lineupRepository.findLatestBefore(race.closeAt()))
                .map(RaceLineup::teams)
                .orElse(List.of());
        return new RaceLineupEditor(race, teams, saved.isPresent());
    }
}
