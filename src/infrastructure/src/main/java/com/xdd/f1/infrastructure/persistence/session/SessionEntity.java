package com.xdd.f1.infrastructure.persistence.session;

import com.xdd.f1.infrastructure.persistence.user.UserEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "sessions")
public class SessionEntity {
    @Id
    String hash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    UserEntity user;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "last_used_at")
    Instant lastUsedAt;

    protected SessionEntity() {
    }
}
