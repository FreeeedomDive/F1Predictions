package com.xdd.f1.application.race.lineup;

import com.xdd.f1.domain.race.RaceId;

public class RaceLineupConflictException extends RuntimeException {
    public RaceLineupConflictException(RaceId raceId) {
        super("The lineup changed since you started editing race " + raceId.value() + ". Load the latest lineup before saving.");
    }
}
