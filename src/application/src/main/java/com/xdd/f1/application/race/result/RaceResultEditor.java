package com.xdd.f1.application.race.result;

import com.xdd.f1.domain.prediction.PredictionConditions;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.lineup.Team;
import com.xdd.f1.domain.race.result.RaceResult;

import java.util.List;

public record RaceResultEditor(
        Race race,
        List<Team> teams,
        boolean lineupPrepared,
        PredictionConditions conditions,
        RaceResult result
) {
    public RaceResultEditor {
        teams = List.copyOf(teams);
    }
}
