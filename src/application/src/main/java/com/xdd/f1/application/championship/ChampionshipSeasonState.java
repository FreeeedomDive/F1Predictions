package com.xdd.f1.application.championship;

import com.xdd.f1.domain.championship.ChampionshipDriverStandings;
import com.xdd.f1.domain.championship.ChampionshipSeason;

public record ChampionshipSeasonState(
        ChampionshipSeason configuration,
        ChampionshipDriverStandings driverStandings,
        ChampionshipTeamInfo teamInfo
) {
}
