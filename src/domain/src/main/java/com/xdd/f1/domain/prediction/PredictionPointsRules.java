package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.RaceType;

public final class PredictionPointsRules {
    public static final int MAX_TENTH_PLACE_POINTS = 25;

    private PredictionPointsRules() {
    }

    public static int maxTotalPoints(int season, RaceType type) {
        var maximum = switch (season) {
            case 2023 -> 30;
            case 2024, 2025, 2026, 2027 -> 55;
            default -> throw new DomainValidationException("Prediction rules are not defined for season " + season);
        };
        return applySprintCoefficient(season, type, maximum);
    }

    public static int applySprintCoefficient(int season, RaceType type, int totalPoints) {
        return type == RaceType.SPRINT && season >= 2025 ? totalPoints * 3 / 10 : totalPoints;
    }
}
