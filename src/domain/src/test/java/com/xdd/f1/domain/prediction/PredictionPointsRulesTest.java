package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.race.RaceType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionPointsRulesTest {
    @ParameterizedTest
    @CsvSource({
            "2023, RACE, 30",
            "2024, RACE, 55",
            "2024, SPRINT, 55",
            "2025, RACE, 55",
            "2025, SPRINT, 16",
            "2026, SPRINT, 16",
            "2027, RACE, 55",
            "2027, SPRINT, 16"
    })
    void calculatesMaximumSavedTotalFromSeasonRules(int season, RaceType type, int expected) {
        assertThat(PredictionPointsRules.maxTotalPoints(season, type)).isEqualTo(expected);
    }
}
