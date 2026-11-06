package com.xdd.f1.contract.race;

import java.time.Instant;
import java.util.UUID;

public record RaceOpenedEvent(UUID raceId, String raceName, String raceType, Instant closeAt) {
}
