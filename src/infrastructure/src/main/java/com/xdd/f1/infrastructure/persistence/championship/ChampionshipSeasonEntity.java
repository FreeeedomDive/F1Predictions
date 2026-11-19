package com.xdd.f1.infrastructure.persistence.championship;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "championship_seasons")
public class ChampionshipSeasonEntity {
    @Id
    int season;

    @Column(name = "rules_version", nullable = false)
    String rulesVersion;

    @Column(name = "stage", nullable = false)
    String stage;

    @Column(name = "stage_started", nullable = false)
    boolean stageStarted;

    @Column(name = "is_open", nullable = false)
    boolean isOpen;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pre_season_roster", nullable = false, columnDefinition = "jsonb")
    List<String> preSeasonRoster;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mid_season_roster", nullable = false, columnDefinition = "jsonb")
    List<String> midSeasonRoster;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "driver_standings", columnDefinition = "jsonb")
    List<String> driverStandings;

    @Column(name = "last_synced_round")
    Integer lastSyncedRound;

    @Column(name = "standings_updated_at")
    Instant standingsUpdatedAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    protected ChampionshipSeasonEntity() {
    }
}
