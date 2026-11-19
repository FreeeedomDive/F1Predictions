package com.xdd.f1.domain.championship;

import com.xdd.f1.domain.error.DomainValidationException;

import java.util.List;
import java.util.Objects;

public record ChampionshipSeason(
        int season,
        ChampionshipRulesVersion rulesVersion,
        ChampionshipStage stage,
        boolean stageStarted,
        boolean isOpen,
        List<String> preSeasonRoster,
        List<String> midSeasonRoster
) {
    public ChampionshipSeason {
        if (season <= 0) {
            throw new DomainValidationException("Championship season must be greater than zero");
        }
        Objects.requireNonNull(rulesVersion, "Championship rules version must not be null");
        Objects.requireNonNull(stage, "Championship stage must not be null");
        if (!stageStarted && (isOpen || stage != ChampionshipStage.PRE_SEASON)) {
            throw new DomainValidationException("Only a closed pre-season stage can be unstarted");
        }
        preSeasonRoster = List.copyOf(preSeasonRoster);
        midSeasonRoster = List.copyOf(midSeasonRoster);
    }

    public boolean isPublished(ChampionshipStage predictionStage) {
        return switch (predictionStage) {
            case PRE_SEASON -> stage == ChampionshipStage.MID_SEASON || (stageStarted && !isOpen);
            case MID_SEASON -> stage == ChampionshipStage.MID_SEASON && !isOpen;
        };
    }

    public List<String> roster(ChampionshipStage predictionStage) {
        return switch (predictionStage) {
            case PRE_SEASON -> preSeasonRoster;
            case MID_SEASON -> midSeasonRoster;
        };
    }
}
