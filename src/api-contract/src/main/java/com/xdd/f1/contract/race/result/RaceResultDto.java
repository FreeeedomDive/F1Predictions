package com.xdd.f1.contract.race.result;

import java.util.UUID;

public record RaceResultDto(UUID raceId, String rulesVersion, RaceResultValuesDto result, RaceResultValuesDto draft) {
}
