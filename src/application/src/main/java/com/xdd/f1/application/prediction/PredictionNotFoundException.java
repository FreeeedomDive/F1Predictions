package com.xdd.f1.application.prediction;

import com.xdd.f1.domain.prediction.PredictionId;

public class PredictionNotFoundException extends RuntimeException {
    public PredictionNotFoundException(PredictionId id) {
        super("Prediction not found with id: " + id.value());
    }
}
