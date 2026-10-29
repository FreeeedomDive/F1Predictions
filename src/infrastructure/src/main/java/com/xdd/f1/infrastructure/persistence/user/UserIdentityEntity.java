package com.xdd.f1.infrastructure.persistence.user;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "user_identities")
public class UserIdentityEntity {
    @EmbeddedId
    UserIdentityEntityId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    UserEntity user;

    protected UserIdentityEntity() {
    }
}
