package com.xdd.f1.application.race.lineup;

import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.RaceLineup;

import java.time.Instant;
import java.util.Optional;

public interface RaceLineupRepository {
    Optional<RaceLineup> findByRaceId(RaceId raceId);
    Optional<RaceLineup> findLatestBefore(Instant closeAt);
    boolean createIfAbsent(RaceLineup lineup);
    void update(RaceLineup lineup);
}
