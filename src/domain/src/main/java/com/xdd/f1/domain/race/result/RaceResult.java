package com.xdd.f1.domain.race.result;

import com.xdd.f1.domain.prediction.RulesVersion;
import com.xdd.f1.domain.race.RaceId;

import java.util.Objects;

public record RaceResult(RaceId raceId, RulesVersion rulesVersion, RaceResultValues result, RaceResultValues draft) {
    public RaceResult {
        Objects.requireNonNull(raceId, "Race ID must not be null");
        Objects.requireNonNull(rulesVersion, "Rules version must not be null");
    }
}
