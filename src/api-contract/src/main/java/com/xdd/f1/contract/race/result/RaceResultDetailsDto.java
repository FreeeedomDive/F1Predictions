package com.xdd.f1.contract.race.result;

import com.xdd.f1.contract.prediction.PredictionPointsDto;
import com.xdd.f1.contract.race.RaceDto;

public record RaceResultDetailsDto(
        RaceDto race,
        int season,
        String rulesVersion,
        RaceResultValuesDto result,
        PredictionPointsDto points
) {
}
