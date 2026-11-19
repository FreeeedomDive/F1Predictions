package com.xdd.f1.application.championship;

import com.xdd.f1.domain.championship.ChampionshipDriverStandings;
import com.xdd.f1.domain.championship.ChampionshipPrediction;
import com.xdd.f1.domain.championship.ChampionshipSeason;
import com.xdd.f1.domain.league.LeagueParticipantId;

import java.util.List;

public record ChampionshipDetails(
        int season,
        ChampionshipSeason configuration,
        ChampionshipDriverStandings driverStandings,
        OwnPrediction preSeasonPrediction,
        OwnPrediction midSeasonPrediction,
        List<ParticipantPoints> rows
) {
    public static ChampionshipDetails empty(int season) {
        return new ChampionshipDetails(season, null, null, null, null, List.of());
    }

    public record OwnPrediction(ChampionshipPrediction prediction, List<Integer> points) {
    }

    public record ParticipantPoints(LeagueParticipantId participantId, String displayName, int totalPoints) {
    }
}
