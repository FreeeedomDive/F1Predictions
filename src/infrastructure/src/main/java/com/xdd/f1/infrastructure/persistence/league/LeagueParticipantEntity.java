package com.xdd.f1.infrastructure.persistence.league;

import com.xdd.f1.infrastructure.persistence.user.UserEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "league_participants")
public class LeagueParticipantEntity {
    @Id
    @Column(name = "id", nullable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    LeagueEntity league;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    UserEntity user;

    @Column(name = "display_name", nullable = false)
    String displayName;

    @Column(name = "joined_at", nullable = false)
    Instant joinedAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
