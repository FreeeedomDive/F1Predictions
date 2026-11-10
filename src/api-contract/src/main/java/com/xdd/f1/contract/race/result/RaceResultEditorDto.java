package com.xdd.f1.contract.race.result;

import com.xdd.f1.contract.prediction.PredictionConditionsDto;
import com.xdd.f1.contract.race.RaceDto;
import com.xdd.f1.contract.race.lineup.RaceLineupTeamDto;

import java.util.List;

public record RaceResultEditorDto(
        RaceDto race,
        int season,
        List<RaceLineupTeamDto> teams,
        boolean lineupPrepared,
        PredictionConditionsDto conditions,
        RaceResultDto result
) {
}
