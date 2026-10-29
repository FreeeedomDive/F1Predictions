package com.xdd.f1.infrastructure.persistence.user;

import jakarta.persistence.*;

@Embeddable
public record UserIdentityEntityId(
    @Column(name = "source", nullable = false)
    String source,

    @Column(name = "source_id", nullable = false)
    String sourceId
)
{}