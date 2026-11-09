package com.xdd.f1.application.prediction;

import com.xdd.f1.domain.race.RaceId;

public class PredictionsClosedException extends RuntimeException {
    public PredictionsClosedException(RaceId raceId) {
        super("Predictions are not open for race with id: " + raceId.value());
    }
}
