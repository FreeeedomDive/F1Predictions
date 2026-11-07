package com.xdd.f1.contract.race;

import java.util.List;
import java.util.UUID;

public record RaceDriversReplacedEvent(
        UUID raceId,
        String raceName,
        String raceType,
        String teamName,
        List<DriverReplacementDto> replacements
) {
}
