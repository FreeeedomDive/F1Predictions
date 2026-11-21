package com.xdd.f1.domain.bingo;

import java.util.Objects;
import java.util.UUID;

public record BingoCardId(UUID value) {
    public BingoCardId {
        Objects.requireNonNull(value, "Bingo card ID must not be null");
    }
}
