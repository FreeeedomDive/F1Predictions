package com.xdd.f1.infrastructure.persistence.race.result;

import java.math.BigDecimal;
import java.util.List;

public record RaceResultValuesData(
        List<ClassificationEntry> classification,
        Integer safetyCars,
        BigDecimal firstPlaceLead
) {
    public record ClassificationEntry(String driverName, String status) {
    }
}
