package com.xdd.f1.application.standings;

import com.xdd.f1.domain.prediction.PredictionPoints;
import com.xdd.f1.domain.race.RaceId;

public record SeasonRacePoints(RaceId raceId, PredictionPoints points) {
}
