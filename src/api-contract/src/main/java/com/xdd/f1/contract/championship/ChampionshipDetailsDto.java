package com.xdd.f1.contract.championship;

import com.xdd.f1.contract.race.lineup.RaceLineupTeamDto;

import java.util.List;

public record ChampionshipDetailsDto(
        int season,
        boolean configured,
        String rulesVersion,
        ChampionshipStageDto currentStage,
        boolean stageStarted,
        boolean predictionsOpen,
        List<String> preSeasonRoster,
        List<String> midSeasonRoster,
        List<String> driverStandings,
        ChampionshipStandingsUpdateDto standingsUpdate,
        ChampionshipPredictionDto preSeasonPrediction,
        ChampionshipPredictionDto midSeasonPrediction,
        List<ChampionshipParticipantPointsDto> rows,
        List<RaceLineupTeamDto> teams,
        List<String> currentDrivers
) {
}
