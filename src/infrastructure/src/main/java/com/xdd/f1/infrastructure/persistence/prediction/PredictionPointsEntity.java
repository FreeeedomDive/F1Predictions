package com.xdd.f1.infrastructure.persistence.prediction;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "prediction_points")
public class PredictionPointsEntity {
    @Column(name = "rules_version", nullable = false)
    String rulesVersion;

    @Id
    @Column(name = "prediction_id", nullable = false)
    UUID predictionId;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "total_points", nullable = false)
    int totalPoints;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prediction_id", insertable = false, updatable = false)
    PredictionEntity prediction;

    @Column(name = "tenth_place_points")
    Integer tenthPlacePoints;

    @Column(name = "dnfs_points")
    Integer dnfsPoints;

    @Column(name = "safety_cars_points")
    Integer safetyCarsPoints;

    @Column(name = "first_place_lead_points")
    Integer firstPlaceLeadPoints;

    @Column(name = "team_mates_points")
    Integer teamMatesPoints;

    @Column(name = "driver_position_points")
    Integer driverPositionPoints;

    protected PredictionPointsEntity() {
    }
}
