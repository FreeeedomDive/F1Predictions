package com.xdd.f1.application.prediction;

import com.xdd.f1.domain.prediction.PredictionConditions;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.lineup.Team;

import java.time.Instant;
import java.util.List;

public record RaceDetails(
        Race race,
        List<Team> teams,
        boolean lineupPrepared,
        PredictionConditions conditions,
        boolean predictionsOpen,
        Instant serverTime,
        List<String> startingGrid
) {
    public RaceDetails {
        teams = List.copyOf(teams);
        startingGrid = List.copyOf(startingGrid);
    }
}
