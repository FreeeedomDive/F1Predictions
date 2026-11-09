package com.xdd.f1.infrastructure.persistence.prediction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PredictionConditionsJpaRepository extends JpaRepository<PredictionConditionsEntity, UUID> {
    @Modifying
    @Query("""
        insert into PredictionConditionsEntity (raceId, version, positionPredictionDriver)
        values (:raceId, 0L, :driverName)
        on conflict (raceId) do nothing
        """)
    int createIfAbsent(@Param("raceId") UUID raceId, @Param("driverName") String driverName);
}
