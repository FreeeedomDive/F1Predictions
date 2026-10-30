package com.xdd.f1.domain.session;

import com.xdd.f1.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;

public record Session(
        String hash,
        UserId userId,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt
) {

    public Session {
        Objects.requireNonNull(hash);
        if (hash.isBlank()){
            throw new IllegalArgumentException("Hash cannot be blank");
        }
        Objects.requireNonNull(userId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "Expiration time must be after creation time"
            );
        }
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
