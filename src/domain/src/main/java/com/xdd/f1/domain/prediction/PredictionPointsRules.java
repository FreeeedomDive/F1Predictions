package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.race.RaceType;

public final class PredictionPointsRules {
    public static final int MAX_TENTH_PLACE_POINTS = 25;
    private static final int[] TENTH_PLACE_POINTS = {0, 1, 2, 4, 6, 8, 10, 12, 15, 18, 25, 18, 15, 12, 10, 8, 6, 4, 2, 1, 1};

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

    public static int tenthPlacePoints(int position) {
        return position >= 0 && position < TENTH_PLACE_POINTS.length ? TENTH_PLACE_POINTS[position] : 0;
    }
}
