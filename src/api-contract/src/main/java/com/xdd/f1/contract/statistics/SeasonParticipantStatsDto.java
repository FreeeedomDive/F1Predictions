package com.xdd.f1.contract.statistics;

import java.util.UUID;

public record SeasonParticipantStatsDto(UUID participantId, String participantName, int score) {
}
