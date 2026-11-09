package com.xdd.f1.infrastructure.persistence.prediction;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "race_prediction_conditions")
public class PredictionConditionsEntity {
    @Id
    @Column(name = "race_id", nullable = false)
    UUID raceId;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "position_prediction_driver")
    String positionPredictionDriver;

    protected PredictionConditionsEntity() {
    }
}
