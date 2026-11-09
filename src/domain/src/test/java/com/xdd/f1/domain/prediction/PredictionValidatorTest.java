package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.lineup.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PredictionValidatorTest {
    @Test
    void accepts2023TenthPlaceAndFirstDnfWithoutAdditionalConditions() {
        var values = new PredictionValues(new Driver("Driver 1"), new DnfPrediction(drivers(1)), null, null, null);
        assertValid(2023, values, null);
        assertNoPositionCondition(2023);
    }

    @Test
    void accepts2024DnfIncidentsGapAndTeamSelections() {
        var values = new PredictionValues(new Driver("Driver 1"), new DnfPrediction(drivers(5)),
                SafetyCarsCount.ZERO, null, BigDecimal.ZERO);
        assertValid(2024, values, teamSelections());
        assertNoPositionCondition(2024);
    }

    @Test
    void accepts2025DnfIncidentsGapAndTeamSelections() {
        var values = new PredictionValues(new Driver("Driver 1"), new DnfPrediction(drivers(5)),
                SafetyCarsCount.ONE, null, new BigDecimal("1.25"));
        assertValid(2025, values, teamSelections());
        assertNoPositionCondition(2025);
    }

    @Test
    void accepts2026PositionPredictionAndCreatesItsCondition() {
        var values = new PredictionValues(new Driver("Driver 1"), new DnfPrediction(drivers(5)),
                SafetyCarsCount.ZERO, 22, BigDecimal.ZERO);
        assertValid(2026, values, null);
        assertPositionCondition(2026);
    }

    @Test
    void accepts2027PositionPredictionAndCreatesItsCondition() {
        var values = new PredictionValues(new Driver("Driver 1"), new DnfPrediction(drivers(5)),
                SafetyCarsCount.TWO, 10, new BigDecimal("2.5"));
        assertValid(2027, values, null);
        assertPositionCondition(2027);
    }

    @ParameterizedTest
    @ValueSource(ints = {2023, 2024, 2025, 2026, 2027})
    void acceptsAbsentCategories(int season) {
        assertValid(season, new PredictionValues(null, null, null, null, null), null);
    }

    @Test
    void rejectsNobodyAndMultipleFirstDnfSelectionsIn2023() {
        assertThatThrownBy(() -> PredictionValidator.validate(2023, withDnf(new DnfPrediction(List.of()))))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> PredictionValidator.validate(2023, withDnf(new DnfPrediction(drivers(5)))))
                .isInstanceOf(DomainValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {2024, 2025, 2026, 2027})
    void acceptsNobodyButRejectsOneDnfSelection(int season) {
        assertThatCode(() -> PredictionValidator.validate(season, withDnf(new DnfPrediction(List.of()))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PredictionValidator.validate(season, withDnf(new DnfPrediction(drivers(1)))))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsDuplicateDnfDrivers() {
        assertThatThrownBy(() -> new DnfPrediction(List.of(new Driver("Driver 1"), new Driver("Driver 1"))))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void requiresExplicitRulesForANewSeason() {
        assertThatThrownBy(() -> PredictionValidator.validate(2028,
                prediction(new PredictionValues(null, null, null, null, null), null)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("2028");
    }

    @Test
    void rejectsCategoriesUnavailableIn2023() {
        assertThatThrownBy(() -> PredictionValidator.validate(2023,
                prediction(new PredictionValues(null, null, SafetyCarsCount.ZERO, null, null), null)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Safety cars");
        assertThatThrownBy(() -> PredictionValidator.validate(2023,
                prediction(new PredictionValues(null, null, null, null, BigDecimal.ZERO), null)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("First place lead");
        assertThatThrownBy(() -> PredictionValidator.validate(2023,
                prediction(new PredictionValues(null, null, null, 1, null), null)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Driver position");
        assertThatThrownBy(() -> PredictionValidator.validate(2023,
                prediction(new PredictionValues(null, null, null, null, null), teamSelections())))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Team predictions");
    }

    @ParameterizedTest
    @ValueSource(ints = {2024, 2025})
    void rejectsPositionPredictionBefore2026(int season) {
        assertThatThrownBy(() -> PredictionValidator.validate(season,
                prediction(new PredictionValues(null, null, null, 1, null), null)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Driver position");
    }

    @ParameterizedTest
    @ValueSource(ints = {2026, 2027})
    void rejectsTeamSelectionsAfter2025(int season) {
        assertThatThrownBy(() -> PredictionValidator.validate(season,
                prediction(new PredictionValues(null, null, null, null, null), teamSelections())))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Team predictions");
    }

    @ParameterizedTest
    @ValueSource(ints = {2024, 2025})
    void rejectsIncompleteOrDuplicateTeamSelections(int season) {
        var values = new PredictionValues(null, null, null, null, null);
        assertThatThrownBy(() -> PredictionValidator.validate(season, prediction(values, List.of("Driver 1"))))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> PredictionValidator.validate(season,
                prediction(values, IntStream.range(0, 10).mapToObj(index -> "Driver 1").toList())))
                .isInstanceOf(DomainValidationException.class);
    }

    private void assertValid(int season, PredictionValues values, List<String> teams) {
        assertThatCode(() -> PredictionValidator.validate(season, prediction(values, teams))).doesNotThrowAnyException();
    }

    private void assertNoPositionCondition(int season) {
        assertThat(PredictionValidator.createConditions(season, raceId(), () -> {
            throw new AssertionError("This season must not choose a position prediction driver");
        })).isEmpty();
    }

    private void assertPositionCondition(int season) {
        var raceId = raceId();
        var driver = new Driver("Driver 1");
        assertThat(PredictionValidator.createConditions(season, raceId, () -> driver))
                .contains(new PredictionConditions(raceId, driver));
    }

    private Prediction withDnf(DnfPrediction dnf) {
        return prediction(new PredictionValues(null, dnf, null, null, null), null);
    }

    private Prediction prediction(PredictionValues values, List<String> teams) {
        return new Prediction(new PredictionId(UUID.randomUUID()), new LeagueId(UUID.randomUUID()),
                new LeagueParticipantId(UUID.randomUUID()), raceId(), values, teams);
    }

    private RaceId raceId() {
        return new RaceId(UUID.randomUUID());
    }

    private List<Driver> drivers(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> new Driver("Driver " + index)).toList();
    }

    private List<String> teamSelections() {
        return drivers(10).stream().map(Driver::name).toList();
    }
}
