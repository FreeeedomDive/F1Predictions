package com.xdd.f1.infrastructure.persistence.league;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeagueJpaRepository extends JpaRepository<LeagueEntity, UUID> {
    Optional<LeagueEntity> findBySourceAndSourceId(LeagueSourceType source, String sourceId);
}
