package com.xdd.f1.application.prediction;

import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.prediction.PredictionId;
import com.xdd.f1.domain.prediction.PredictionPoints;
import com.xdd.f1.domain.race.RaceId;

import java.util.List;
import java.util.Optional;

public interface PredictionPointsRepository {
    Optional<PredictionPoints> findByPredictionId(PredictionId predictionId);
    List<PredictionPoints> findAllByRaceId(RaceId raceId);
    List<ParticipantRacePoints> findAllByLeagueAndSeason(LeagueId leagueId, int season);
    void create(PredictionPoints points);
    void update(PredictionPoints points);
}
