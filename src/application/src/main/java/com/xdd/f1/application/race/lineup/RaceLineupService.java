package com.xdd.f1.application.race.lineup;

import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.lineup.RaceLineup;
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
    private final Clock clock;

    public RaceLineupService(RaceRepository raceRepository, RaceLineupRepository lineupRepository, Clock clock) {
        this.raceRepository = raceRepository;
        this.lineupRepository = lineupRepository;
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
        var race = raceRepository.findById(lineup.raceId())
                .orElseThrow(() -> new RaceNotFoundException(lineup.raceId()));
        if (race.status() != RaceStatus.SCHEDULED && race.status() != RaceStatus.OPEN) {
            throw new RaceLineupNotEditableException(race.id(), race.status());
        }

        if (!lineupRepository.createIfAbsent(lineup)) {
            var existing = lineupRepository.findByRaceId(lineup.raceId())
                    .orElseThrow(() -> new RaceLineupNotFoundException(lineup.raceId()));
            if (!existing.equals(lineup)) {
                lineupRepository.update(lineup);
            }
        }
        return lineup;
    }

    private RaceLineupEditor toEditor(Race race) {
        var saved = lineupRepository.findByRaceId(race.id());
        var teams = saved.or(() -> lineupRepository.findLatestBefore(race.closeAt()))
                .map(RaceLineup::teams)
                .orElse(List.of());
        return new RaceLineupEditor(race, teams, saved.isPresent());
    }
}
