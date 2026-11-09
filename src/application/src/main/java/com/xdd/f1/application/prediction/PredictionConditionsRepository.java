package com.xdd.f1.application.prediction;

import com.xdd.f1.domain.prediction.PredictionConditions;
import com.xdd.f1.domain.race.RaceId;

import java.util.Optional;

public interface PredictionConditionsRepository {
    Optional<PredictionConditions> findByRaceId(RaceId raceId);
    boolean createIfAbsent(PredictionConditions conditions);
    void update(PredictionConditions conditions);
}
