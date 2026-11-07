package com.xdd.f1.infrastructure.persistence.race.lineup;

import org.hibernate.query.TypedParameterValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RaceLineupJpaRepository extends JpaRepository<RaceLineupEntity, UUID> {
    @Modifying
    @Query("""
        insert into RaceLineupEntity (raceId, teams, version)
        values (:raceId, :teams, 0L)
        on conflict (raceId) do nothing
        """)
    int createIfAbsent(@Param("raceId") UUID raceId, @Param("teams") TypedParameterValue<?> teams);
}
