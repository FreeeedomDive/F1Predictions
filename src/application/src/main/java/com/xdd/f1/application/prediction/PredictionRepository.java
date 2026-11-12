package com.xdd.f1.application.prediction;

import com.xdd.f1.application.statistics.SeasonStatsPrediction;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.prediction.Prediction;
import com.xdd.f1.domain.prediction.PredictionId;
import com.xdd.f1.domain.race.RaceId;

import java.util.List;
import java.util.Optional;

public interface PredictionRepository {
    Optional<Prediction> findById(PredictionId id);
    Optional<Prediction> findByLeagueAndRaceAndParticipant(LeagueId leagueId, RaceId raceId, LeagueParticipantId participantId);
    List<Prediction> findAllByRaceId(RaceId raceId);
    List<SeasonStatsPrediction> findSeasonStatistics(LeagueId leagueId, int season);
    void create(Prediction prediction);
    void update(Prediction prediction);
}
