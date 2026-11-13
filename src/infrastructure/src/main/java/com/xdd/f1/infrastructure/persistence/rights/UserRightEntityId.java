package com.xdd.f1.infrastructure.persistence.rights;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record UserRightEntityId(
    @Column(name = "user_id", nullable = false)
    UUID userId,

    @Column(name = "right_name", nullable = false)
    String rightName
)
{}
