package com.xdd.f1.infrastructure.persistence.rights;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface UserRightsProjection {
    UUID getUserId();

    @Nullable String getDisplayName();

    @Nullable String getRightName();
}
