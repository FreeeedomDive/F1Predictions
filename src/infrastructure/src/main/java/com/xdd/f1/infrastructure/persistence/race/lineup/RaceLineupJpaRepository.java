package com.xdd.f1.infrastructure.persistence.race.lineup;

import org.hibernate.query.TypedParameterValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RaceLineupJpaRepository extends JpaRepository<RaceLineupEntity, UUID> {
    @Query("""
        select lineup
        from RaceLineupEntity lineup
        join RaceEntity race on race.id = lineup.raceId
        where race.closeAt < :closeAt
        order by race.closeAt desc, race.id
        limit 1
        """)
    Optional<RaceLineupEntity> findLatestBefore(@Param("closeAt") Instant closeAt);

    @Modifying
    @Query("""
        insert into RaceLineupEntity (raceId, teams, version)
        values (:raceId, :teams, 0L)
        on conflict (raceId) do nothing
        """)
    int createIfAbsent(@Param("raceId") UUID raceId, @Param("teams") TypedParameterValue<?> teams);
}
