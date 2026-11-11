package com.xdd.f1.infrastructure.persistence.prediction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PredictionPointsJpaRepository extends JpaRepository<PredictionPointsEntity, UUID> {
    @Query("""
        select points from PredictionPointsEntity points
        join points.prediction prediction
        where prediction.raceId = :raceId
        """)
    List<PredictionPointsEntity> findAllByRaceId(@Param("raceId") UUID raceId);

    @Query("""
        select points.predictionId as predictionId,
               points.rulesVersion as rulesVersion,
               points.totalPoints as totalPoints,
               points.tenthPlacePoints as tenthPlacePoints,
               points.dnfsPoints as dnfPoints,
               points.safetyCarsPoints as safetyCarsPoints,
               points.firstPlaceLeadPoints as firstPlaceLeadPoints,
               points.driverPositionPoints as driverPositionPoints,
               points.teamMatesPoints as teamMatesPoints,
               participant.id as participantId,
               participant.displayName as participantName,
               participant.joinedAt as participantJoinedAt,
               race.id as raceId,
               race.season as season,
               race.round as round,
               race.type as type,
               race.name as name,
               race.status as status,
               race.openAt as openAt,
               race.closeAt as closeAt
        from PredictionPointsEntity points
        join points.prediction prediction
        join prediction.participant participant
        join prediction.race race
        where prediction.leagueId = :leagueId and race.season = :season
        order by race.closeAt, race.round, race.type, participant.joinedAt, prediction.id
        """)
    List<SeasonPointsProjection> findAllByLeagueAndSeason(@Param("leagueId") UUID leagueId, @Param("season") int season);
}
