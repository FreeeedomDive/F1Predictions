package com.xdd.f1.domain.race.result;

import com.xdd.f1.domain.prediction.RulesVersion;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.Driver;

import java.util.List;
import java.util.Objects;

public record RaceResult(
        RaceId raceId,
        RulesVersion rulesVersion,
        RaceResultValues result,
        RaceResultValues draft,
        List<Driver> startingGrid
) {
    public RaceResult {
        Objects.requireNonNull(raceId, "Race ID must not be null");
        Objects.requireNonNull(rulesVersion, "Rules version must not be null");
        if (startingGrid != null) {
            startingGrid = List.copyOf(startingGrid);
        }
    }

    public RaceResult(RaceId raceId, RulesVersion rulesVersion, RaceResultValues result, RaceResultValues draft) {
        this(raceId, rulesVersion, result, draft, null);
    }
}
