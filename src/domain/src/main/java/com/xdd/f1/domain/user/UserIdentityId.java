package com.xdd.f1.domain.user;

import com.xdd.f1.domain.error.DomainValidationException;

import java.util.Objects;

public record UserIdentityId(UserIdentitySource source, String sourceId) {

    public UserIdentityId {
        Objects.requireNonNull(source, "Identity source must not be null");
        Objects.requireNonNull(sourceId, "Identity source ID must not be null");
        if (sourceId.isBlank()) {
            throw new DomainValidationException(
                    "Identity source ID must not be blank"
            );
        }
    }
}
