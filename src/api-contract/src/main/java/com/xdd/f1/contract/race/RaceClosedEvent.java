package com.xdd.f1.contract.race;

import java.util.UUID;

public record RaceClosedEvent(UUID raceId, String raceName, String raceType) {
}
