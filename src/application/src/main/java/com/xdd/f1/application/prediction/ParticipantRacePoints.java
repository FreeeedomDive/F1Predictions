package com.xdd.f1.application.prediction;

import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.prediction.PredictionPoints;
import com.xdd.f1.domain.race.Race;

import java.time.Instant;

public record ParticipantRacePoints(
        LeagueParticipantId participantId,
        String participantName,
        Instant participantJoinedAt,
        Race race,
        PredictionPoints points
) {
}
