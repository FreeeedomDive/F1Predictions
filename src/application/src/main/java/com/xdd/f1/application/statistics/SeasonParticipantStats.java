package com.xdd.f1.application.statistics;

import com.xdd.f1.domain.league.LeagueParticipantId;

public record SeasonParticipantStats(LeagueParticipantId participantId, String participantName, int score) {
}
