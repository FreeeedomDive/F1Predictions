package com.xdd.f1.domain.championship;

import com.xdd.f1.domain.error.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.xdd.f1.domain.championship.ChampionshipRulesVersion.V2026;
import static com.xdd.f1.domain.championship.ChampionshipRulesVersion.V2027;
import static com.xdd.f1.domain.championship.ChampionshipStage.MID_SEASON;
import static com.xdd.f1.domain.championship.ChampionshipStage.PRE_SEASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChampionshipPointsCalculatorTest {
    private static final List<String> LEGACY_STANDINGS = List.of("VER", "NOR", "LEC", "HAM", "RUS");
    private static final List<String> LEGACY_PREDICTION = List.of("NOR", "VER", "LEC", "HAM", "RUS");

    @Test
    void matchesAntiClownBotStandingsAndPredictionFixture() {
        var season = new ChampionshipSeason(2026, V2026, MID_SEASON, true, false, List.of(), List.of());

        assertThat(ChampionshipPointsCalculator.calculate(season, PRE_SEASON, LEGACY_STANDINGS, LEGACY_PREDICTION))
                .containsExactly(14, 14, 20, 20, 20);
        assertThat(ChampionshipPointsCalculator.calculate(season, MID_SEASON, LEGACY_STANDINGS, LEGACY_PREDICTION))
                .containsExactly(6, 6, 10, 10, 10);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 20, 10",
            "1, 14, 6",
            "2, 11, 4",
            "3, 8, 2",
            "4, 5, 1",
            "5, 2, 0",
            "6, 0, 0"
    })
    void usesTheLegacyPointsTables(int difference, int preSeasonPoints, int midSeasonPoints) {
        var season = new ChampionshipSeason(2026, V2026, MID_SEASON, true, false, List.of(), List.of());
        var standings = List.of("VER", "NOR", "LEC", "HAM", "RUS", "ALO", "PIA");
        var prediction = List.of(standings.get(difference));

        assertThat(ChampionshipPointsCalculator.calculate(season, PRE_SEASON, standings, prediction))
                .containsExactly(preSeasonPoints);
        assertThat(ChampionshipPointsCalculator.calculate(season, MID_SEASON, standings, prediction))
                .containsExactly(midSeasonPoints);
    }

    @ParameterizedTest
    @CsvSource({
            "PRE_SEASON, false, false, false, false",
            "PRE_SEASON, true,  true,  false, false",
            "PRE_SEASON, true,  false, true,  false",
            "MID_SEASON, true,  true,  true,  false",
            "MID_SEASON, true,  false, true,  true"
    })
    void scoresOnlyPublishedStages(ChampionshipStage stage, boolean stageStarted, boolean isOpen,
                                   boolean preSeasonPublished, boolean midSeasonPublished) {
        var season = new ChampionshipSeason(2026, V2026, stage, stageStarted, isOpen, List.of(), List.of());

        assertThat(ChampionshipPointsCalculator.calculate(season, PRE_SEASON, LEGACY_STANDINGS, List.of("VER")))
                .containsExactlyElementsOf(preSeasonPublished ? List.of(20) : List.of());
        assertThat(ChampionshipPointsCalculator.calculate(season, MID_SEASON, LEGACY_STANDINGS, List.of("VER")))
                .containsExactlyElementsOf(midSeasonPublished ? List.of(10) : List.of());
    }

    @Test
    void filtersTheOfficialOrderByEachStageRosterOnlyUnderV2027() {
        var standings = List.of("PIA", "NOR", "VER", "HAM", "LEC");
        var preSeasonRoster = List.of("VER", "NOR");
        var midSeasonRoster = List.of("VER", "PIA", "NOR");
        var legacy = new ChampionshipSeason(2027, V2026, MID_SEASON, true, false, preSeasonRoster, midSeasonRoster);
        var current = new ChampionshipSeason(2027, V2027, MID_SEASON, true, false, preSeasonRoster, midSeasonRoster);

        assertThat(ChampionshipPointsCalculator.calculate(legacy, PRE_SEASON, standings, List.of("NOR", "VER")))
                .containsExactly(14, 14);
        assertThat(ChampionshipPointsCalculator.calculate(current, PRE_SEASON, standings, List.of("NOR", "VER")))
                .containsExactly(20, 20);
        assertThat(ChampionshipPointsCalculator.calculate(current, MID_SEASON, standings, List.of("NOR", "VER", "PIA")))
                .containsExactly(6, 6, 4);
        assertThat(standings).containsExactly("PIA", "NOR", "VER", "HAM", "LEC");
    }

    @Test
    void scoresCopiedPredictionAsMidSeasonWithoutMovingTheRemainingPredictedDrivers() {
        var copiedPrediction = List.of("HAM", "NOR", "VER");
        var season = new ChampionshipSeason(2027, V2027, MID_SEASON, true, false, copiedPrediction, List.of("NOR", "VER"));

        assertThat(ChampionshipPointsCalculator.calculate(season, MID_SEASON,
                List.of("HAM", "NOR", "VER"), copiedPrediction)).containsExactly(0, 6, 6);
    }

    @Test
    void distinguishesMissingStandingsFromADriverAbsentInTheStandings() {
        var season = new ChampionshipSeason(2026, V2026, MID_SEASON, true, false, List.of(), List.of());

        assertThat(ChampionshipPointsCalculator.calculate(season, MID_SEASON, List.of(), List.of("PIA"))).isEmpty();
        assertThat(ChampionshipPointsCalculator.calculate(season, MID_SEASON, LEGACY_STANDINGS, List.of("PIA")))
                .containsExactly(0);
        assertThat(ChampionshipPointsCalculator.calculate(season, MID_SEASON, LEGACY_STANDINGS, List.of())).isEmpty();
    }

    @Test
    void usesOnlyKnownPersistedRulesVersions() {
        assertThat(ChampionshipRulesVersion.fromValue("championship-v2026")).isEqualTo(V2026);
        assertThat(ChampionshipRulesVersion.fromValue("championship-v2027")).isEqualTo(V2027);
        assertThatThrownBy(() -> ChampionshipRulesVersion.fromValue("championship-v2099"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("championship-v2099");
    }
}
