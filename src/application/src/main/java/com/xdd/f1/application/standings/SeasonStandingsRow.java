package com.xdd.f1.application.standings;

import com.xdd.f1.domain.league.LeagueParticipantId;

import java.util.List;

public record SeasonStandingsRow(
        LeagueParticipantId participantId,
        String participantName,
        int totalPoints,
        int maximumTenthPlaceCount,
        List<SeasonRacePoints> results,
        List<SeasonPodium> previousPodiums
) {
    public SeasonStandingsRow {
        results = List.copyOf(results);
        previousPodiums = List.copyOf(previousPodiums);
    }
}
