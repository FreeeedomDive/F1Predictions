package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;

public record UserLeague(
        LeagueId leagueId,
        LeagueParticipantId participantId,
        String leagueName,
        String participantName
) {
}
