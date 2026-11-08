package com.xdd.f1.application.race.lineup;

import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;

public class RaceLineupNotEditableException extends RuntimeException {
    public RaceLineupNotEditableException(RaceId raceId, RaceStatus status) {
        super("Lineup can only be edited for a scheduled or open race: " + raceId.value()
                + " (current status: " + status + ")");
    }
}
