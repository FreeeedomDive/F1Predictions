package com.xdd.f1.infrastructure.persistence.race.result;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RaceResultJpaRepository extends JpaRepository<RaceResultEntity, UUID> {
    @Query("""
        select result.raceId
        from RaceResultEntity result
        where result.race.season = :season
          and result.result is not null
        """)
    List<UUID> findPublishedRaceIdsBySeason(@Param("season") int season);
}
