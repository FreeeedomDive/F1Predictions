package com.xdd.f1.application.prediction;

import com.xdd.f1.domain.prediction.PredictionId;

public class PredictionPointsNotFoundException extends RuntimeException {
    public PredictionPointsNotFoundException(PredictionId predictionId) {
        super("Prediction points not found for prediction " + predictionId.value());
    }
}
