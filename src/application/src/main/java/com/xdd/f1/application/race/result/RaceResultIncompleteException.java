package com.xdd.f1.application.race.result;

import com.xdd.f1.domain.race.RaceId;

public class RaceResultIncompleteException extends RuntimeException {
    public RaceResultIncompleteException(RaceId raceId, String reason) {
        super("Race result is incomplete for race " + raceId.value() + ": " + reason);
    }
}
