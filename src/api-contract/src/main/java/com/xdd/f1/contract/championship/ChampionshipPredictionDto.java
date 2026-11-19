package com.xdd.f1.contract.championship;

import java.util.List;

public record ChampionshipPredictionDto(
        ChampionshipStageDto stage,
        List<String> driverStandings,
        ChampionshipPredictionSourceDto predictionSource,
        List<Integer> points
) {
}
