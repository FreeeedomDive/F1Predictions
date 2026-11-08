package com.xdd.f1.infrastructure.persistence.race.lineup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "race_lineups")
public class RaceLineupEntity {
    @Id
    @Column(name = "race_id", nullable = false)
    UUID raceId;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "teams", nullable = false, columnDefinition = "jsonb")
    List<RaceLineupTeamData> teams;

    protected RaceLineupEntity() {
    }
}
