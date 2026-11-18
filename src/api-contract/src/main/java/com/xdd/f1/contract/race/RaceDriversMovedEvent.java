package com.xdd.f1.contract.race;

import java.util.List;
import java.util.UUID;

public record RaceDriversMovedEvent(
        UUID raceId, String raceName, String raceType, String teamName, String teamColor,
        List<String> departures, List<String> arrivals
) {
}
