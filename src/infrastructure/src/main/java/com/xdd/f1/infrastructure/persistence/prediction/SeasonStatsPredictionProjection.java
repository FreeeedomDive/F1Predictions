package com.xdd.f1.infrastructure.persistence.prediction;

public interface SeasonStatsPredictionProjection {
    PredictionEntity getPrediction();
    String getParticipantName();
    Integer getTenthPlacePoints();
}
