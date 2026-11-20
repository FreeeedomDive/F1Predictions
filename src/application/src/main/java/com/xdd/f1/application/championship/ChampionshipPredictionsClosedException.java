package com.xdd.f1.application.championship;

import com.xdd.f1.domain.championship.ChampionshipStage;

public class ChampionshipPredictionsClosedException extends RuntimeException {
    public ChampionshipPredictionsClosedException(int season, ChampionshipStage stage) {
        super("Championship season " + season + ": predictions are not open for " + stage);
    }
}
