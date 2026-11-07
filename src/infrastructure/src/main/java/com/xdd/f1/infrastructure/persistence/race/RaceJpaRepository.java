package com.xdd.f1.infrastructure.persistence.race;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RaceJpaRepository extends JpaRepository<RaceEntity, UUID> {
    List<RaceEntity> findAllBySeasonOrderByOpenAtAsc(int season);

    List<RaceEntity> findAllBySeasonAndStatusOrderByOpenAtAsc(int season, String status);

    List<RaceEntity> findAllByStatusAndOpenAtLessThanEqualOrderByOpenAtAsc(String status, Instant now);

    List<RaceEntity> findAllByStatusAndCloseAtLessThanEqualOrderByCloseAtAsc(String status, Instant now);
}
