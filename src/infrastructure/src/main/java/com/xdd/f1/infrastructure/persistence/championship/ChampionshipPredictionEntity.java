package com.xdd.f1.infrastructure.persistence.championship;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "championship_predictions")
public class ChampionshipPredictionEntity {
    @EmbeddedId
    ChampionshipPredictionEntityId id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ordered_drivers", nullable = false, columnDefinition = "jsonb")
    List<String> orderedDrivers;

    @Column(name = "prediction_source", nullable = false)
    String predictionSource;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    protected ChampionshipPredictionEntity() {
    }
}
