package com.xdd.f1.infrastructure.persistence.user;

import com.xdd.f1.infrastructure.persistence.league.LeagueParticipantEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @Column(name = "id", nullable = false)
    UUID id;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    List<UserIdentityEntity> userIdentities;

    @OneToMany(mappedBy = "user")
    List<LeagueParticipantEntity> leaguesParticipant;

    protected UserEntity() {
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
