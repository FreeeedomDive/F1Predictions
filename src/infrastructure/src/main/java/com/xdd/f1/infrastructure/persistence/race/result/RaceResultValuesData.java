package com.xdd.f1.infrastructure.persistence.race.result;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RaceResultValuesData(
        List<String> classification,
        Map<String, String> driverStatuses,
        Integer safetyCars,
        BigDecimal firstPlaceLead
) {
}
