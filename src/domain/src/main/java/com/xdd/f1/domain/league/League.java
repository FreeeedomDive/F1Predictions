package com.xdd.f1.domain.league;

import java.time.Instant;
import java.util.Objects;

public record League(LeagueId id, String name, LeagueSource source, LeagueSettings settings, Instant createdAt) {
    public League {
        Objects.requireNonNull(id, "League ID must not be null");
        Objects.requireNonNull(name, "League name must not be null");
        Objects.requireNonNull(source, "League source must not be null");
        Objects.requireNonNull(settings, "League settings must not be null");
        Objects.requireNonNull(createdAt, "Creation time must not be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("League name must not be blank");
        }
    }
}
