package com.xdd.f1.contract.race;

import java.time.Instant;
import java.util.UUID;

public record RaceDto(
        UUID id,
        String name,
        RaceTypeDto type,
        RaceStatusDto status,
        Instant openAt,
        Instant closeAt
) {
}
