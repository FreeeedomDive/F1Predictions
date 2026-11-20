package com.xdd.f1.application.championship;

import com.xdd.f1.domain.league.LeagueParticipantId;

public record ChampionshipParticipantPoints(LeagueParticipantId participantId, String displayName, int totalPoints) {
}
