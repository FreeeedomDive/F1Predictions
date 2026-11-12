package com.xdd.f1.application.standings;

import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.race.Race;

import java.util.List;

public record SeasonStandings(
        int season,
        List<Race> events,
        List<SeasonStandingsRow> rows,
        LeagueParticipantId currentLeaderParticipantId,
        int currentLeaderPoints,
        int pointsLeft,
        List<SeasonParticipantChart> charts,
        List<Integer> championChart
) {
    public SeasonStandings {
        events = List.copyOf(events);
        rows = List.copyOf(rows);
        charts = List.copyOf(charts);
        championChart = List.copyOf(championChart);
    }

    public static SeasonStandings empty(int season) {
        return new SeasonStandings(season, List.of(), List.of(), null, 0, 0, List.of(), List.of());
    }
}
