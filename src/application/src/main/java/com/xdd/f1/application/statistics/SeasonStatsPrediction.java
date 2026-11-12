package com.xdd.f1.application.statistics;

import com.xdd.f1.domain.prediction.Prediction;

public record SeasonStatsPrediction(Prediction prediction, String participantName, Integer tenthPlacePoints) {
}
