package com.xdd.f1.application.championship;

import com.xdd.f1.domain.championship.ChampionshipPredictionSource;
import com.xdd.f1.domain.league.LeagueParticipantId;

public record ChampionshipPredictionStatus(
        LeagueParticipantId participantId,
        String displayName,
        boolean hasPreSeasonPrediction,
        ChampionshipPredictionSource midSeasonPredictionSource
) {
}
