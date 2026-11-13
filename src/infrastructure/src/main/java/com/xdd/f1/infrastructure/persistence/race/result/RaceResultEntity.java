package com.xdd.f1.infrastructure.persistence.race.result;

import com.xdd.f1.infrastructure.persistence.race.RaceEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "race_results")
public class RaceResultEntity {
    @Column(name = "rules_version", nullable = false)
    String rulesVersion;

    @Id
    @Column(name = "race_id", nullable = false)
    UUID raceId;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", insertable = false, updatable = false)
    RaceEntity race;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    RaceResultValuesData result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft", columnDefinition = "jsonb")
    RaceResultValuesData draft;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "starting_grid", columnDefinition = "jsonb")
    List<String> startingGrid;

    protected RaceResultEntity() {
    }
}
