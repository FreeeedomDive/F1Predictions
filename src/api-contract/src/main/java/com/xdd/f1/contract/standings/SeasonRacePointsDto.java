package com.xdd.f1.contract.standings;

import com.xdd.f1.contract.prediction.PredictionPointsDto;

import java.util.UUID;

public record SeasonRacePointsDto(UUID raceId, PredictionPointsDto points) {
}
