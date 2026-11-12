package com.xdd.f1.infrastructure.persistence.prediction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PredictionJpaRepository extends JpaRepository<PredictionEntity, UUID> {
    Optional<PredictionEntity> findByLeagueIdAndRaceIdAndParticipantId(UUID leagueId, UUID raceId, UUID participantId);
    List<PredictionEntity> findAllByRaceId(UUID raceId);

    @Query("""
        select prediction as prediction,
               participant.displayName as participantName,
               points.tenthPlacePoints as tenthPlacePoints
        from PredictionEntity prediction
        join prediction.participant participant
        join prediction.race race
        left join PredictionPointsEntity points on points.predictionId = prediction.id
        where prediction.leagueId = :leagueId and race.season = :season
        order by race.closeAt, race.round, race.type, participant.joinedAt, prediction.id
        """)
    List<SeasonStatsPredictionProjection> findSeasonStatistics(@Param("leagueId") UUID leagueId, @Param("season") int season);
}
