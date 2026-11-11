package com.xdd.f1.infrastructure.persistence.prediction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PredictionPointsJpaRepository extends JpaRepository<PredictionPointsEntity, UUID> {
    @Query("""
        select points from PredictionPointsEntity points
        join points.prediction prediction
        where prediction.raceId = :raceId
        """)
    List<PredictionPointsEntity> findAllByRaceId(@Param("raceId") UUID raceId);
}
