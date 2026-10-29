package com.xdd.f1.infrastructure.persistence.league;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeagueParticipantJpaRepository extends JpaRepository<LeagueParticipantEntity, UUID> {
    Optional<LeagueParticipantEntity> findByLeague_IdAndUser_Id(
            UUID leagueId,
            UUID userId
    );
}
