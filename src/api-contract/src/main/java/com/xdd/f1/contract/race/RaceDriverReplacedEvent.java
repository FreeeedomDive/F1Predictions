package com.xdd.f1.contract.race;

import java.util.UUID;

public record RaceDriverReplacedEvent(
        UUID raceId,
        String raceName,
        String raceType,
        String previousDriverName,
        String newDriverName
) {
}
