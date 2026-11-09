package com.xdd.f1.infrastructure.persistence.prediction;

import com.xdd.f1.infrastructure.persistence.league.LeagueParticipantEntity;
import com.xdd.f1.infrastructure.persistence.race.RaceEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "predictions")
public class PredictionEntity {
    @Id
    @Column(name = "id", nullable = false)
    UUID id;

    @Column(name = "league_id", nullable = false)
    UUID leagueId;

    @Column(name = "participant_id", nullable = false)
    UUID participantId;

    @Column(name = "race_id", nullable = false)
    UUID raceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "league_id", referencedColumnName = "league_id", insertable = false, updatable = false),
            @JoinColumn(name = "participant_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    LeagueParticipantEntity participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", insertable = false, updatable = false)
    RaceEntity race;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "tenth_place_picked_driver")
    String tenthPlacePickedDriver;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dnf_picked_drivers", columnDefinition = "jsonb")
    List<String> dnfPickedDrivers;

    @Column(name = "safety_cars_prediction")
    String safetyCarsPrediction;

    @Column(name = "driver_position_prediction")
    Integer driverPositionPrediction;

    @Column(name = "first_place_lead_prediction", precision = 9, scale = 3)
    BigDecimal firstPlaceLeadPrediction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "teams_picked_drivers", columnDefinition = "jsonb")
    List<String> teamsPickedDrivers;

    protected PredictionEntity() {
    }
}
