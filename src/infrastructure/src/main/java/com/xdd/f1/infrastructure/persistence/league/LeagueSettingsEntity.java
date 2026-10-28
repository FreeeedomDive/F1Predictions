package com.xdd.f1.infrastructure.persistence.league;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "league_settings")
public class LeagueSettingsEntity {
    @Id
    @Column(name = "league_id", nullable = false)
    UUID id;

    @Column(name = "notifications_channel_id")
    String notificationsChannelId;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    LeagueEntity league;

    protected LeagueSettingsEntity() {
    }
}
