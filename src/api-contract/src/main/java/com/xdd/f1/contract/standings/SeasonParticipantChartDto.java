package com.xdd.f1.contract.standings;

import java.util.List;
import java.util.UUID;

public record SeasonParticipantChartDto(UUID participantId, List<Integer> points) {
}
