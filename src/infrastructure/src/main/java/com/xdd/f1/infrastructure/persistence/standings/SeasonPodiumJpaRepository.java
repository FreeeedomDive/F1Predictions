package com.xdd.f1.infrastructure.persistence.standings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SeasonPodiumJpaRepository extends JpaRepository<SeasonPodiumEntity, SeasonPodiumEntityId> {
    @Query("""
        select podium from SeasonPodiumEntity podium
        where podium.id.leagueId = :leagueId and podium.id.season < :season
        order by podium.id.season
        """)
    List<SeasonPodiumEntity> findAllByLeagueBeforeSeason(@Param("leagueId") UUID leagueId, @Param("season") int season);
}
