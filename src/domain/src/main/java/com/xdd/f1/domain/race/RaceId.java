package com.xdd.f1.domain.race;

import java.util.Objects;
import java.util.UUID;

public record RaceId(UUID value) {
    public RaceId {
        Objects.requireNonNull(value, "Race ID must not be null");
    }
}
