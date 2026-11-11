package com.xdd.f1.application.standings;

import com.xdd.f1.domain.league.LeagueParticipantId;

import java.util.List;

public record SeasonParticipantChart(LeagueParticipantId participantId, List<Integer> points) {
    public SeasonParticipantChart {
        points = List.copyOf(points);
    }
}
