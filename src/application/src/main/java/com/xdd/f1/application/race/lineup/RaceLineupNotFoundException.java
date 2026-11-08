package com.xdd.f1.application.race.lineup;

import com.xdd.f1.domain.race.RaceId;

public class RaceLineupNotFoundException extends RuntimeException {
    public RaceLineupNotFoundException(RaceId raceId) {
        super("Race lineup not found for race with id: " + raceId.value());
    }
}
