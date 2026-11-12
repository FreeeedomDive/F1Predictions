package com.xdd.f1.contract.statistics;

import java.math.BigDecimal;
import java.util.UUID;

public record SeasonLeadGapPredictionStatsDto(
        UUID participantId,
        String participantName,
        String raceName,
        BigDecimal predictedGap,
        BigDecimal actualGap,
        BigDecimal difference
) {
}
