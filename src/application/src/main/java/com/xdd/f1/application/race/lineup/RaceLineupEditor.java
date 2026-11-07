package com.xdd.f1.application.race.lineup;

import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.lineup.Team;

import java.util.List;

public record RaceLineupEditor(Race race, List<Team> teams, boolean prepared) {
    public RaceLineupEditor {
        teams = List.copyOf(teams);
    }
}
