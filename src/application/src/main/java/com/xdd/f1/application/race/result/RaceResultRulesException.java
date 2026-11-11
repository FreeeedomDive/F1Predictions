package com.xdd.f1.application.race.result;

import com.xdd.f1.domain.prediction.RulesVersion;
import com.xdd.f1.domain.race.RaceId;

public class RaceResultRulesException extends RuntimeException {
    public RaceResultRulesException(RaceId raceId, RulesVersion version) {
        super("Rules version " + version.value() + " cannot be used for race " + raceId.value());
    }
}
