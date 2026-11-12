package com.xdd.f1.contract.standings;

import java.util.UUID;

public interface StandingsApi {
    SeasonStandingsDto getStandings(UUID leagueId, int season);
}
