package com.xdd.f1.application.statistics;

import com.xdd.f1.domain.league.LeagueParticipantId;

import java.math.BigDecimal;

public record SeasonLeadGapPredictionStats(
        LeagueParticipantId participantId,
        String participantName,
        String raceName,
        BigDecimal predictedGap,
        BigDecimal actualGap,
        BigDecimal difference
) {
}
