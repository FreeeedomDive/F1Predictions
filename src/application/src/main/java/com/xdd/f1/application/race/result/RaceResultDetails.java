package com.xdd.f1.application.race.result;

import com.xdd.f1.domain.prediction.PredictionPoints;
import com.xdd.f1.domain.prediction.RulesVersion;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.result.RaceResultValues;

public record RaceResultDetails(Race race, RulesVersion rulesVersion, RaceResultValues result, PredictionPoints points) {
}
