package com.xdd.f1.contract.race;

import java.util.List;
import java.util.UUID;

public record RaceStartingGridUpdatedEvent(
        UUID raceId,
        String raceName,
        String raceType,
        int season,
        List<String> startingGrid,
        boolean firstPublication
) {
}
