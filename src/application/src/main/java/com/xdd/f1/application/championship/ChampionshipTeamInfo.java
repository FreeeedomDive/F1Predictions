package com.xdd.f1.application.championship;

import com.xdd.f1.domain.race.lineup.Team;

import java.util.List;

public record ChampionshipTeamInfo(List<Team> teams, List<String> currentDrivers) {
    public static ChampionshipTeamInfo empty() {
        return new ChampionshipTeamInfo(List.of(), List.of());
    }
}
