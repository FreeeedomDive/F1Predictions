package com.xdd.f1.application.race.result;

import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;

public class RaceResultOperationNotAllowedException extends RuntimeException {
    public RaceResultOperationNotAllowedException(RaceId raceId, RaceStatus status, String operation) {
        super("Cannot " + operation + " for race " + raceId.value() + " (current status: " + status + ")");
    }
}
