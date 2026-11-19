package com.xdd.f1.contract.championship;

import java.time.Instant;
import java.util.List;

public record ChampionshipSeasonDto(
        int season,
        boolean configured,
        String rulesVersion,
        Window window,
        List<String> driverStandings,
        StandingsUpdate standingsUpdate
) {
    public record Window(
            ChampionshipStageDto stage,
            boolean stageStarted,
            boolean isOpen,
            List<String> preSeasonRoster,
            List<String> midSeasonRoster
    ) {
    }

    public record StandingsUpdate(Integer lastSyncedRound, Instant updatedAt) {
    }
}
