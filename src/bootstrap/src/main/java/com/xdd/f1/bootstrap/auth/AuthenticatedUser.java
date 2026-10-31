package com.xdd.f1.bootstrap.auth;

import com.xdd.f1.domain.user.UserId;

import java.util.Objects;

public record AuthenticatedUser(UserId id) {

    static final String REQUEST_ATTRIBUTE = AuthenticatedUser.class.getName();

    public AuthenticatedUser {
        Objects.requireNonNull(id, "Authenticated user ID must not be null");
    }
}
