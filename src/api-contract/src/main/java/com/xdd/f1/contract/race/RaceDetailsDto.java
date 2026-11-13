package com.xdd.f1.contract.race;

import com.xdd.f1.contract.prediction.PredictionConditionsDto;
import com.xdd.f1.contract.race.lineup.RaceLineupTeamDto;

import java.time.Instant;
import java.util.List;

public record RaceDetailsDto(
        RaceDto race,
        int season,
        List<RaceLineupTeamDto> teams,
        boolean lineupPrepared,
        PredictionConditionsDto conditions,
        boolean predictionsOpen,
        Instant serverTime,
        List<String> startingGrid
) {
}
