package com.xdd.f1.application.prediction;

import com.xdd.f1.domain.race.RaceId;

public class PredictionConditionsNotFoundException extends RuntimeException {
    public PredictionConditionsNotFoundException(RaceId raceId) {
        super("Prediction conditions not found for race with id: " + raceId.value());
    }
}
