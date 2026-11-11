package com.xdd.f1.contract.race.result;

import java.math.BigDecimal;
import java.util.List;

public record RaceResultValuesDto(List<RaceClassificationEntryDto> classification, Integer safetyCars, BigDecimal firstPlaceLead) {
}
