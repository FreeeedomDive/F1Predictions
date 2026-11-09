package com.xdd.f1.domain.prediction;

import java.util.Objects;
import java.util.UUID;

public record PredictionId(UUID value) {
    public PredictionId {
        Objects.requireNonNull(value, "Prediction ID must not be null");
    }
}
