package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.DriverReplacement;

import java.util.List;
import java.util.Objects;

public record PredictionConditions(RaceId raceId, Driver positionPredictionDriver) {
    public PredictionConditions {
        Objects.requireNonNull(raceId, "Race ID must not be null");
    }

    public PredictionConditions replaceDrivers(List<DriverReplacement> replacements) {
        var driver = replacements.stream().filter(pair -> pair.previousDriver().equals(positionPredictionDriver))
                .map(DriverReplacement::newDriver).findFirst().orElse(positionPredictionDriver);
        return new PredictionConditions(raceId, driver);
    }
}
