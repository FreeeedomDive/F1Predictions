package com.xdd.f1.domain.bingo;

import java.util.Objects;
import java.util.UUID;

public record BingoBoardId(UUID value) {
    public BingoBoardId {
        Objects.requireNonNull(value, "Bingo board ID must not be null");
    }
}
