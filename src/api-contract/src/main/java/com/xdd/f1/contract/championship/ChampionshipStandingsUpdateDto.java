package com.xdd.f1.contract.championship;

import java.time.Instant;

public record ChampionshipStandingsUpdateDto(Integer lastSyncedRound, Instant updatedAt) {
}
