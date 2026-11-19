package com.xdd.f1.domain.championship;

import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;

import java.util.List;
import java.util.Objects;

public record ChampionshipPrediction(
        LeagueId leagueId,
        int season,
        LeagueParticipantId participantId,
        ChampionshipStage stage,
        List<String> orderedDrivers,
        ChampionshipPredictionSource predictionSource
) {
    public ChampionshipPrediction {
        Objects.requireNonNull(leagueId, "League ID must not be null");
        Objects.requireNonNull(participantId, "League participant ID must not be null");
        Objects.requireNonNull(stage, "Championship stage must not be null");
        Objects.requireNonNull(predictionSource, "Championship prediction source must not be null");
        orderedDrivers = List.copyOf(orderedDrivers);
    }
}
