package com.xdd.f1.contract.championship;

import java.util.UUID;

public record ChampionshipPredictionStatusDto(
        UUID participantId,
        String displayName,
        boolean hasPreSeasonPrediction,
        ChampionshipPredictionSourceDto midSeasonPredictionSource
) {
}
