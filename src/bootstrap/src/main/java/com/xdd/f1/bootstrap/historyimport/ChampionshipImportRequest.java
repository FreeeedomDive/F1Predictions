package com.xdd.f1.bootstrap.historyimport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ChampionshipImportRequest(
        @NotNull UUID leagueId,
        @NotBlank String rulesVersion,
        @NotBlank String currentStage,
        @NotNull Boolean predictionsOpen,
        @NotNull List<@NotNull String> preSeasonRoster,
        @NotNull List<@NotNull String> midSeasonRoster,
        @NotNull List<@NotNull String> driverStandings,
        @NotNull List<@NotNull @Valid ChampionshipImportPrediction> predictions
) {
}
