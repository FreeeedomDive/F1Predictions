package com.xdd.f1.application.race.result;

import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.result.RaceResult;

import java.util.List;
import java.util.Optional;

public interface RaceResultRepository {
    Optional<RaceResult> findByRaceId(RaceId raceId);
    List<RaceId> findPublishedRaceIdsBySeason(int season);
    List<RaceResult> findAllPublishedBySeason(int season);
    void create(RaceResult result);
    void update(RaceResult result);
}
