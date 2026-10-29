package com.xdd.f1.contract.league;

import java.time.Instant;
import java.util.UUID;

public record LeagueParticipantDto(
        UUID id,
        UUID leagueId,
        UUID userId,
        String displayName,
        Instant joinedAt
) {
}
