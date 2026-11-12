package com.xdd.f1.application.standings;

import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;

public record SeasonPodiumEntry(
        LeagueId leagueId,
        int season,
        LeagueParticipantId firstPlaceParticipantId,
        LeagueParticipantId secondPlaceParticipantId,
        LeagueParticipantId thirdPlaceParticipantId
) {
}
