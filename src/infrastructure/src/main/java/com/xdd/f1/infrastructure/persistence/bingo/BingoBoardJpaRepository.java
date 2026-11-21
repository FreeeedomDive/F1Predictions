package com.xdd.f1.infrastructure.persistence.bingo;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BingoBoardJpaRepository extends JpaRepository<BingoBoardEntity, UUID> {
    @EntityGraph(attributePaths = "cells")
    Optional<BingoBoardEntity> findByLeagueIdAndSeasonAndParticipantId(UUID leagueId, int season, UUID participantId);

    @EntityGraph(attributePaths = "cells")
    List<BingoBoardEntity> findAllBySeason(int season);
}
