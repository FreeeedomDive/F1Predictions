package com.xdd.f1.domain.user;

import java.time.Instant;
import java.util.Objects;

public record User(UserId userId, Instant createdAt) {

    public User {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(createdAt, "Creation time must not be null");
    }
}
