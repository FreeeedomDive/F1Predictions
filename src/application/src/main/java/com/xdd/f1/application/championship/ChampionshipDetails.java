package com.xdd.f1.application.championship;

import com.xdd.f1.domain.championship.ChampionshipDriverStandings;
import com.xdd.f1.domain.championship.ChampionshipSeason;

import java.util.List;

public record ChampionshipDetails(
        int season,
        ChampionshipSeason configuration,
        ChampionshipDriverStandings driverStandings,
        ChampionshipScoredPrediction preSeasonPrediction,
        ChampionshipScoredPrediction midSeasonPrediction,
        List<ChampionshipParticipantPoints> rows,
        ChampionshipTeamInfo teamInfo
) {
    public static ChampionshipDetails empty(int season) {
        return new ChampionshipDetails(season, null, null, null, null, List.of(), ChampionshipTeamInfo.empty());
    }
}
