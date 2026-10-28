package com.xdd.f1.infrastructure.persistence.league;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "leagues")
public class LeagueEntity {
    @Id
    @Column(name = "id", nullable = false)
    UUID id;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "source", nullable = false)
    @Enumerated(EnumType.STRING)
    LeagueSourceType source;

    @Column(name = "source_id", nullable = false)
    String sourceId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @OneToOne(
            mappedBy = "league",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            optional = false
    )
    LeagueSettingsEntity settings;

    protected LeagueEntity() {
    }
}
