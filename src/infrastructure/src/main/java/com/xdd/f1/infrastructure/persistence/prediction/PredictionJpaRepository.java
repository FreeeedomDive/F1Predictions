package com.xdd.f1.infrastructure.persistence.prediction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PredictionJpaRepository extends JpaRepository<PredictionEntity, UUID> {
    Optional<PredictionEntity> findByLeagueIdAndRaceIdAndParticipantId(UUID leagueId, UUID raceId, UUID participantId);
    List<PredictionEntity> findAllByRaceId(UUID raceId);
}
