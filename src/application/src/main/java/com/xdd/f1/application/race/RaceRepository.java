package com.xdd.f1.application.race;

import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RaceRepository {
    Optional<Race> findById(RaceId id);
    List<Race> findAllBySeason(int season);
    List<Race> findFinishedBySeason(int season);
    List<Race> findRacesToOpen(Instant now);
    List<Race> findRacesToClose(Instant now);
    void create(Race race);
    void update(Race race);
}
