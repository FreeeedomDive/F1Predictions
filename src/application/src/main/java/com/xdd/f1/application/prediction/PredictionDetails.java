package com.xdd.f1.application.prediction;

import com.xdd.f1.domain.prediction.Prediction;

public record PredictionDetails(Prediction prediction, String participantName, String leagueName) {
}
