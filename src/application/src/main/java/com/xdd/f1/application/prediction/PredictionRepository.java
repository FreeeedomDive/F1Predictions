package com.xdd.f1.application.prediction;

import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.prediction.Prediction;
import com.xdd.f1.domain.race.RaceId;

import java.util.List;
import java.util.Optional;

public interface PredictionRepository {
    Optional<Prediction> findByLeagueAndRaceAndParticipant(LeagueId leagueId, RaceId raceId, LeagueParticipantId participantId);
    List<Prediction> findAllByRaceId(RaceId raceId);
    void create(Prediction prediction);
    void update(Prediction prediction);
}
