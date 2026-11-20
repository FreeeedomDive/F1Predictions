package com.xdd.f1.bootstrap.historyimport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ChampionshipImportPrediction(
        @NotNull UUID participantId,
        @NotBlank String stage,
        @NotNull List<@NotNull String> driverStandings
) {
}
