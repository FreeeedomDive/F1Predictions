package com.xdd.f1.domain.race;

import java.time.Instant;
import java.util.Objects;

public record Race(
        RaceId id,
        int season,
        int round,
        RaceType type,
        String name,
        RaceStatus status,
        Instant openAt,
        Instant closeAt
) {
    public Race {
        Objects.requireNonNull(id, "Race ID must not be null");
        if (season <= 0) {
            throw new IllegalArgumentException("Season must be greater than zero");
        }
        if (round <= 0) {
            throw new IllegalArgumentException("Round must be greater than zero");
        }
        Objects.requireNonNull(type, "Race type must not be null");
        Objects.requireNonNull(name, "Race name must not be null");
        Objects.requireNonNull(status, "Race status must not be null");
        Objects.requireNonNull(openAt, "Race open time must not be null");
        Objects.requireNonNull(closeAt, "Race close time must not be null");

        if (!openAt.isBefore(closeAt)) {
            throw new IllegalArgumentException("Open time must be before close time");
        }
    }
}
