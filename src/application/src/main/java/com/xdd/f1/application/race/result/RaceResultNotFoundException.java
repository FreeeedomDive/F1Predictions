package com.xdd.f1.application.race.result;

import com.xdd.f1.domain.race.RaceId;

public class RaceResultNotFoundException extends RuntimeException {
    public RaceResultNotFoundException(RaceId raceId) {
        super("Race result not found for race " + raceId.value());
    }
}
