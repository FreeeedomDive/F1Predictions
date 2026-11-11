package com.xdd.f1.application.standings;

import com.xdd.f1.domain.league.LeagueId;

import java.util.List;

public interface SeasonPodiumRepository {
    List<SeasonPodiumEntry> findAllByLeagueBeforeSeason(LeagueId leagueId, int season);
    void save(SeasonPodiumEntry podium);
}
