package com.xdd.f1.contract.championship;

import java.util.List;
import java.util.UUID;

public record ChampionshipDetailsDto(
        int season,
        boolean configured,
        String rulesVersion,
        ChampionshipSeasonDto.Window window,
        List<String> driverStandings,
        ChampionshipSeasonDto.StandingsUpdate standingsUpdate,
        ChampionshipPredictionDto preSeasonPrediction,
        ChampionshipPredictionDto midSeasonPrediction,
        List<ParticipantPoints> rows
) {
    public record ParticipantPoints(UUID participantId, String displayName, int totalPoints) {
    }
}
