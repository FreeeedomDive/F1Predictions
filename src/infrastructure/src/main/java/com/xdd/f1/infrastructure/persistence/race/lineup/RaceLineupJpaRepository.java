package com.xdd.f1.infrastructure.persistence.race.lineup;

import org.hibernate.query.TypedParameterValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceLineupJpaRepository extends JpaRepository<RaceLineupEntity, UUID> {
    @Query("""
        select lineup
        from RaceLineupEntity lineup
        join RaceEntity race on race.id = lineup.raceId
        where race.season = :season and race.type = :raceType
            and race.closeAt <= :now
        order by race.closeAt desc, race.round desc
        """)
    List<RaceLineupEntity> findStartedBySeasonAndRaceTypeOrderByCloseAtDesc(
            @Param("season") int season, @Param("raceType") String raceType, @Param("now") Instant now
    );

    @Query("""
        select lineup
        from RaceLineupEntity lineup
        join RaceEntity race on race.id = lineup.raceId
        where race.season = :season and race.type = :raceType
        order by race.closeAt asc, race.round asc
        limit 1
        """)
    Optional<RaceLineupEntity> findFirstBySeasonAndRaceTypeOrderByCloseAtAsc(
            @Param("season") int season, @Param("raceType") String raceType
    );

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
