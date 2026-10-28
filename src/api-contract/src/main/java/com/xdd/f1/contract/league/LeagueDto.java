package com.xdd.f1.contract.league;

import java.time.Instant;
import java.util.UUID;

public record LeagueDto(
        UUID id,
        String name,
        LeagueSettingsDto settings,
        Instant createdAt
) {
}
