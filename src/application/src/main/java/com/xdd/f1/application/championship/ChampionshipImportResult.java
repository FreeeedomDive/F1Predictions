package com.xdd.f1.application.championship;

public record ChampionshipImportResult(
        int preSeasonPredictions,
        int midSeasonPredictions,
        boolean newSeason,
        int newPredictions
) {
}
