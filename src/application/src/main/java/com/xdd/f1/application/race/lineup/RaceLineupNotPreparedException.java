package com.xdd.f1.application.race.lineup;

import com.xdd.f1.domain.race.RaceId;

public class RaceLineupNotPreparedException extends RuntimeException {
    public RaceLineupNotPreparedException(RaceId raceId) {
        super("Prepare a lineup before opening predictions for race " + raceId.value()
                + ": no earlier lineup is available");
    }
}
