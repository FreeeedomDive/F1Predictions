package com.xdd.f1.infrastructure.persistence.race;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceJpaRepository extends JpaRepository<RaceEntity, UUID> {
    Optional<RaceProjection> findProjectedById(UUID id);

    List<RaceProjection> findAllBySeasonOrderByOpenAtAsc(int season);

    List<RaceProjection> findAllBySeasonAndStatusOrderByOpenAtAsc(int season, String status);

    List<RaceProjection> findAllByStatusAndOpenAtLessThanEqualOrderByOpenAtAsc(String status, Instant now);

    List<RaceProjection> findAllByStatusAndCloseAtLessThanEqualOrderByCloseAtAsc(String status, Instant now);

    List<RaceProjection> findAllByStatusInAndOpenAtLessThanEqualAndCloseAtGreaterThanOrderByCloseAtAsc(
            List<String> statuses, Instant openAt, Instant closeAt);
}
