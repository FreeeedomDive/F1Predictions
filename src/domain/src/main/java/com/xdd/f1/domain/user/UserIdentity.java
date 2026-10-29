package com.xdd.f1.domain.user;

import java.util.Objects;

public record UserIdentity(UserId userId, UserIdentityId identityId) {

    public UserIdentity {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(identityId, "Identity ID must not be null");
    }
}
