package com.xdd.f1.application.race.lineup;

import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.RaceLineup;

import java.util.Optional;

public interface RaceLineupRepository {
    Optional<RaceLineup> findByRaceId(RaceId raceId);
    boolean createIfAbsent(RaceLineup lineup);
    void update(RaceLineup lineup);
}
