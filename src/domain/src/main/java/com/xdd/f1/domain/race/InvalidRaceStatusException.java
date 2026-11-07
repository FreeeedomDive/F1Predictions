package com.xdd.f1.domain.race;

public class InvalidRaceStatusException extends IllegalStateException {

    public InvalidRaceStatusException(RaceStatus currentStatus, RaceStatus requiredStatus, RaceStatus targetStatus) {
        super("Race must be in status " + requiredStatus + " to be changed to " + targetStatus
                + " (current status: " + currentStatus + ")");
    }
}
