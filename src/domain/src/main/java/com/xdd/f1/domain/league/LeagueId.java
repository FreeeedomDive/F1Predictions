package com.xdd.f1.domain.league;

import java.util.Objects;
import java.util.UUID;

public record LeagueId(UUID value) {

    public LeagueId {
        Objects.requireNonNull(value, "League ID must not be null");
    }
}
