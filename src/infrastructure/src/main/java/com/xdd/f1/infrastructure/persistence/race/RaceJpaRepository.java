package com.xdd.f1.infrastructure.persistence.race;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceJpaRepository extends JpaRepository<RaceEntity, UUID> {
    Optional<RaceProjection> findProjectedById(UUID id);

    List<RaceProjection> findAllBySeasonOrderByOpenAtAsc(int season);

    @Query("""
        select race.id as id, race.season as season, race.round as round, race.type as type,
            race.name as name, race.status as status, race.openAt as openAt, race.closeAt as closeAt
        from RaceEntity race
        where race.season = :season and race.type = :type
            and exists (select 1 from RaceLineupEntity lineup where lineup.raceId = race.id)
        order by race.round
        """)
    List<RaceProjection> findBySeasonAndTypeWithSavedLineup(@Param("season") int season, @Param("type") String type);

    List<RaceProjection> findAllBySeasonAndStatusOrderByOpenAtAsc(int season, String status);

    List<RaceProjection> findAllByStatusAndOpenAtLessThanEqualOrderByOpenAtAsc(String status, Instant now);

    List<RaceProjection> findAllByStatusAndCloseAtLessThanEqualOrderByCloseAtAsc(String status, Instant now);

    List<RaceProjection> findAllByStatusInAndOpenAtLessThanEqualAndCloseAtGreaterThanOrderByCloseAtAsc(
            List<String> statuses, Instant openAt, Instant closeAt);
}
