package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.race.*;
import com.xdd.f1.domain.race.lineup.*;
import com.xdd.f1.domain.race.result.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PredictionPointsCalculatorTest {
    private static final LeagueId LEAGUE = new LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @ParameterizedTest(name = "2023: {0}")
    @MethodSource("examples2023")
    void matchesTen2023ProductionExamples(String description, String[] fields) {
        var race = productionRace(2023, fields);
        var result = result(race, new RaceResultValues(productionClassification(fields), null, null));
        var values = new PredictionValues(new Driver(fields[9]), new DnfPrediction(drivers(fields[10])), null, null, null);
        var selected = productionPrediction(race, fields, values, null);
        var expected = new PredictionPoints(selected.id(), result.rulesVersion(), Integer.parseInt(fields[22]),
                Integer.valueOf(fields[17]), Integer.valueOf(fields[18]), null, null, null, null);

        assertProductionPoints(race, result, null, null, List.of(selected), expected);
    }

    @ParameterizedTest(name = "2024: {0}")
    @MethodSource("examples2024")
    void matchesTen2024ProductionExamples(String description, String[] fields) {
        var race = productionRace(2024, fields);
        var result = result(race, new RaceResultValues(productionClassification(fields), Integer.valueOf(fields[6]), new BigDecimal(fields[7])));
        var values = new PredictionValues(new Driver(fields[9]), new DnfPrediction(drivers(fields[10])),
                safetyCars(fields[11]), null, new BigDecimal(fields[13]));
        var selected = productionPrediction(race, fields, values, names(fields[14]));
        var predictions = productionPredictions(race, fields, selected);
        var lineup = productionLineup(race, fields);
        var expected = new PredictionPoints(selected.id(), result.rulesVersion(), Integer.parseInt(fields[22]),
                Integer.valueOf(fields[17]), Integer.valueOf(fields[18]), Integer.valueOf(fields[20]),
                Integer.valueOf(fields[19]), null, Integer.valueOf(fields[21]));

        assertProductionPoints(race, result, null, lineup, predictions, expected);
    }

    @ParameterizedTest(name = "2025: {0}")
    @MethodSource("examples2025")
    void matchesTen2025ProductionExamples(String description, String[] fields) {
        var race = productionRace(2025, fields);
        var result = result(race, new RaceResultValues(productionClassification(fields), Integer.valueOf(fields[6]), new BigDecimal(fields[7])));
        var values = new PredictionValues(new Driver(fields[9]), new DnfPrediction(drivers(fields[10])),
                safetyCars(fields[11]), null, new BigDecimal(fields[13]));
        var selected = productionPrediction(race, fields, values, names(fields[14]));
        var predictions = productionPredictions(race, fields, selected);
        var lineup = productionLineup(race, fields);
        var expected = new PredictionPoints(selected.id(), result.rulesVersion(), Integer.parseInt(fields[22]),
                Integer.valueOf(fields[17]), Integer.valueOf(fields[18]), Integer.valueOf(fields[20]),
                Integer.valueOf(fields[19]), null, Integer.valueOf(fields[21]));

        assertProductionPoints(race, result, null, lineup, predictions, expected);
    }

    @ParameterizedTest(name = "2026: {0}")
    @MethodSource("examples2026")
    void matchesTen2026ProductionExamples(String description, String[] fields) {
        var race = productionRace(2026, fields);
        var result = result(race, new RaceResultValues(productionClassification(fields), Integer.valueOf(fields[6]), new BigDecimal(fields[7])));
        var conditions = new PredictionConditions(race.id(), new Driver(fields[8]));
        var values = new PredictionValues(new Driver(fields[9]), new DnfPrediction(drivers(fields[10])),
                safetyCars(fields[11]), Integer.valueOf(fields[12]), new BigDecimal(fields[13]));
        var selected = productionPrediction(race, fields, values, null);
        var predictions = productionPredictions(race, fields, selected);
        var expected = new PredictionPoints(selected.id(), result.rulesVersion(), Integer.parseInt(fields[22]),
                Integer.valueOf(fields[17]), Integer.valueOf(fields[18]), Integer.valueOf(fields[20]),
                Integer.valueOf(fields[19]), Integer.valueOf(fields[23]), null);

        assertProductionPoints(race, result, conditions, null, predictions, expected);
    }

    @ParameterizedTest(name = "2027-v1: {0}")
    @CsvSource(textBlock = """
        perfect,                false, 10, 18;19;20;21;22, ZERO,       0,  7, 10, 10,     false, 25, 10, 5, 5, 10, 55
        half-second-boundary,   false,  9, 1;2;3;4;18,     ONE,        1,  6, 10, 10.5,   false, 18,  2, 5, 5,  7, 37
        two-second-boundary,    false, 11, 1;2;3;18;19,    TWO,        2,  9, 10, 8,      false, 18,  4, 5, 4,  5, 36
        three-and-half-boundary,false,  8, 1;2;18;19;20,   THREE_PLUS, 4, 10, 10, 13.5,   false, 15,  6, 5, 3,  3, 32
        five-second-boundary,   false, 12, 1;18;19;20;21,  THREE_PLUS, 3, 11, 10, 5,      false, 15,  8, 5, 2,  2, 32
        seven-second-boundary,  false,  1, 1;2;3;4;5,      ONE,        0, 12, 10, 17,     false,  1,  0, 0, 1,  1,  3
        outside-scoring-ranges, false, 22, nobody,         ZERO,       2, 13, 10, 17.001, false,  0,  0, 0, 0,  0,  0
        nobody-and-real-zeros,  false, 20, nobody,         ZERO,       0,  7,  0, 0,      true,   1, 10, 5, 5, 10, 31
        sprint-perfect,         true, 10, 18;19;20;21;22, ZERO,       0,  7, 10, 10,     false, 25, 10, 5, 5, 10, 16
        sprint-round-total,     true,  9, 1;2;3;4;18,     ONE,        1,  6, 10, 12,     false, 18,  2, 5, 4,  7, 10
        """)
    void calculatesTenIndependent2027Examples(String description, boolean sprint, int tenth, String selectedDnfs,
                                             SafetyCarsCount safety, int actualSafety, int position, String actualLead,
                                             String predictedLead, boolean noDnfs, int tenthPoints, int dnfPoints,
                                             int safetyPoints, int leadPoints, int positionPoints, int total) {
        var race = race(2027, sprint);
        var actualDnfs = noDnfs ? List.<Driver>of() : numberedDrivers("18;19;20;21;22");
        var result = result(race, new RaceResultValues(classification(actualDnfs), actualSafety, new BigDecimal(actualLead)));
        var values = new PredictionValues(driver(tenth), new DnfPrediction(numberedDrivers(selectedDnfs)), safety,
                position, new BigDecimal(predictedLead));
        var prediction = prediction(race, LEAGUE, values);
        var expected = new PredictionPoints(prediction.id(), new RulesVersion("2027-v1"), total,
                tenthPoints, dnfPoints, safetyPoints, leadPoints, positionPoints, null);

        assertThat(PredictionPointsCalculator.calculate(race, result, new PredictionConditions(race.id(), driver(7)),
                null, List.of(prediction))).containsExactly(expected);
    }

    @ParameterizedTest
    @CsvSource({"0,5", "0.499,5", "0.5,5", "0.501,4", "1.999,4", "2,4", "2.001,3",
            "3.499,3", "3.5,3", "3.501,2", "4.999,2", "5,2", "5.001,1", "6.999,1", "7,1", "7.001,0"})
    void usesExactLeadBoundariesAndSymmetricErrors(String error, int expected) {
        var race = race(2027, false);
        var actual = new BigDecimal("100");
        var result = result(race, new RaceResultValues(null, null, actual));
        for (var difference : List.of(new BigDecimal(error), new BigDecimal(error).negate())) {
            var prediction = prediction(race, LEAGUE, new PredictionValues(null, null, null, null, actual.add(difference)));
            var points = PredictionPointsCalculator.calculate(race, result, null, null, List.of(prediction)).getFirst();
            assertThat(points.firstPlaceLeadPoints()).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2024, 2025, 2026})
    void isolatesRelativeLeadCompetitionByLeague(int season) {
        var race = race(season, false);
        var result = result(race, new RaceResultValues(null, null, new BigDecimal("10")));
        var otherLeague = new LeagueId(UUID.randomUUID());
        var first = prediction(race, LEAGUE, lead("12"));
        var tied = prediction(race, LEAGUE, lead("8"));
        var otherWinner = prediction(race, otherLeague, lead("10"));
        var otherLoser = prediction(race, otherLeague, lead("12"));
        var absent = prediction(race, LEAGUE, lead(null));
        var calculated = PredictionPointsCalculator.calculate(race, result, null, null,
                List.of(first, tied, otherWinner, otherLoser, absent));
        assertThat(calculated).extracting(PredictionPoints::firstPlaceLeadPoints)
                .containsExactly(5, 5, 5, 0, null);

        var closer = prediction(race, LEAGUE, lead("10"));
        var updated = PredictionPointsCalculator.calculate(race, result, null, null, List.of(first, closer));
        assertThat(updated.getFirst().firstPlaceLeadPoints()).isZero();
    }

    @Test
    void keeps2027LeadIndependentOfOtherPredictions() {
        var race = race(2027, false);
        var result = result(race, new RaceResultValues(null, null, new BigDecimal("10")));
        var first = prediction(race, LEAGUE, lead("12"));
        var tied = prediction(race, LEAGUE, lead("8"));
        var closer = prediction(race, LEAGUE, lead("10"));
        var otherLeague = new LeagueId(UUID.randomUUID());
        var otherWinner = prediction(race, otherLeague, lead("10"));
        var otherLoser = prediction(race, otherLeague, lead("12"));
        var absent = prediction(race, LEAGUE, lead(null));

        assertThat(PredictionPointsCalculator.calculate(race, result, null, null, List.of(first)).getFirst().firstPlaceLeadPoints())
                .isEqualTo(4);
        assertThat(PredictionPointsCalculator.calculate(race, result, null, null,
                List.of(first, tied, closer, otherWinner, otherLoser, absent)))
                .extracting(PredictionPoints::firstPlaceLeadPoints).containsExactly(4, 4, 5, 5, 4, null);
    }

    @Test
    void distinguishesAbsentCategoriesFromNobodyAndZeroAndIgnoresDraftValues() {
        var race = race(2027, false);
        var zeroValues = new PredictionValues(null, new DnfPrediction(List.of()), SafetyCarsCount.ZERO, 7, BigDecimal.ZERO);
        var zero = prediction(race, LEAGUE, zeroValues);
        var absent = prediction(race, LEAGUE, new PredictionValues(null, null, null, null, null));
        var actualZeros = new RaceResultValues(classification(List.of()), 0, BigDecimal.ZERO);
        var result = new RaceResult(race.id(), new RulesVersion("2027-v1"), actualZeros, new RaceResultValues(null, 3, new BigDecimal("100")));
        assertThat(PredictionPointsCalculator.calculate(race, result, null, null, List.of(zero, absent)))
                .containsExactly(new PredictionPoints(zero.id(), result.rulesVersion(), 20, null, 10, 5, 5, null, null),
                        new PredictionPoints(absent.id(), result.rulesVersion(), 0, null, null, null, null, null, null));

        var missingResultCategories = result(race, new RaceResultValues(null, null, null));
        assertThat(PredictionPointsCalculator.calculate(race, missingResultCategories, null, null, List.of(zero)))
                .containsExactly(new PredictionPoints(zero.id(), result.rulesVersion(), 0, null, null, null, null, null, null));
    }

    @Test
    void readsThe2023FirstDnfFromTheEndSkippingDnsAndDq() {
        var race = race(2023, false);
        var result = result(race, new RaceResultValues(classificationWithNonFinishers(), null, null));
        var prediction = prediction(race, LEAGUE, new PredictionValues(null, new DnfPrediction(List.of(driver(20))), null, null, null));
        assertThat(PredictionPointsCalculator.calculate(race, result, null, null, List.of(prediction)).getFirst().dnfsPoints())
                .isEqualTo(5);
        var wrong = prediction(race, LEAGUE, new PredictionValues(null, new DnfPrediction(List.of(driver(18))), null, null, null));
        assertThat(PredictionPointsCalculator.calculate(race, result, null, null, List.of(wrong)).getFirst().dnfsPoints()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"2023,20,5", "2024,18;19;20;21;22,6", "2025,18;19;20;21;22,6",
            "2026,18;19;20;21;22,6", "2027,18;19;20;21;22,6"})
    void doesNotTreatDnsOrDqAsDnf(int season, String selectedDnfs, int expectedPoints) {
        var race = race(season, false);
        var result = result(race, new RaceResultValues(classificationWithNonFinishers(), null, null));
        var prediction = prediction(race, LEAGUE,
                new PredictionValues(null, new DnfPrediction(numberedDrivers(selectedDnfs)), null, null, null));
        assertThat(PredictionPointsCalculator.calculate(race, result, null, null, List.of(prediction)).getFirst().dnfsPoints())
                .isEqualTo(expectedPoints);
    }

    @ParameterizedTest
    @CsvSource({"2024,10", "2025,3", "2026,3", "2027,3"})
    void appliesSprintCoefficientToTheTotalStartingWith2025(int season, int expectedTotal) {
        var race = race(season, true);
        var result = result(race, new RaceResultValues(classification(List.of()), null, null));
        var prediction = prediction(race, LEAGUE, new PredictionValues(null, new DnfPrediction(List.of()), null, null, null));
        var points = PredictionPointsCalculator.calculate(race, result, null, null, List.of(prediction)).getFirst();
        assertThat(points.dnfsPoints()).isEqualTo(10);
        assertThat(points.totalPoints()).isEqualTo(expectedTotal);
    }

    @Test
    void rejectsWrongVersionsMixedRacesAndMissingResultWhileAcceptingNoPredictions() {
        var race = race(2027, false);
        var values = new RaceResultValues(classification(List.of()), 0, BigDecimal.ZERO);
        var result = result(race, values);
        assertThat(PredictionPointsCalculator.calculate(race, result, null, null, List.of())).isEmpty();
        assertThatThrownBy(() -> PredictionPointsCalculator.calculate(race,
                new RaceResult(race.id(), new RulesVersion("2028-v1"), values, null), null, null, List.of()))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("2028-v1");
        assertThatThrownBy(() -> PredictionPointsCalculator.calculate(race,
                new RaceResult(race.id(), new RulesVersion("2026"), values, null), null, null, List.of()))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("does not apply");
        assertThatThrownBy(() -> PredictionPointsCalculator.calculate(race,
                new RaceResult(race.id(), result.rulesVersion(), null, values), null, null, List.of()))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Race result");
        var foreign = prediction(race(2027, false), LEAGUE, lead("1"));
        assertThatThrownBy(() -> PredictionPointsCalculator.calculate(race, result, null, null, List.of(foreign)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("same race");
        var own = prediction(race, LEAGUE, lead("1"));
        assertThatThrownBy(() -> PredictionPointsCalculator.calculate(race, result, null, null, List.of(own, own)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("duplicate");
        var positionPick = prediction(race, LEAGUE, new PredictionValues(null, null, null, 1, null));
        assertThatThrownBy(() -> PredictionPointsCalculator.calculate(race, result,
                new PredictionConditions(race.id(), new Driver("Missing driver")), null, List.of(positionPick)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("classification");
    }

    private static Stream<Arguments> examples2023() throws IOException {
        return historicalExamples(2023);
    }

    private static Stream<Arguments> examples2024() throws IOException {
        return historicalExamples(2024);
    }

    private static Stream<Arguments> examples2025() throws IOException {
        return historicalExamples(2025);
    }

    private static Stream<Arguments> examples2026() throws IOException {
        return historicalExamples(2026);
    }

    private static Stream<Arguments> historicalExamples(int season) throws IOException {
        var examples = new ArrayList<Arguments>();
        try (var input = PredictionPointsCalculatorTest.class.getResourceAsStream("/prediction/points-" + season + ".csv")) {
            assertThat(input).isNotNull();
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                var rows = reader.lines().skip(1).toList();
                assertThat(rows).hasSize(10);
                for (var row : rows) {
                    var fields = row.split("\\|", -1);
                    assertThat(fields).hasSize(24);
                    var name = fields[0] + (Boolean.parseBoolean(fields[3]) ? " (Sprint)" : "");
                    examples.add(Arguments.of(name + " / " + fields[2].substring(0, 8), fields));
                }
            }
        }
        return examples.stream();
    }

    private static Race productionRace(int season, String[] fields) {
        return race(new RaceId(UUID.fromString(fields[1])), season, Boolean.parseBoolean(fields[3]), fields[0]);
    }

    private static List<RaceClassificationEntry> productionClassification(String[] fields) {
        var dnfs = names(fields[5]);
        return names(fields[4]).stream().map(name -> new RaceClassificationEntry(new Driver(name),
                dnfs.contains(name) ? RaceDriverStatus.DNF : RaceDriverStatus.FINISHED)).toList();
    }

    private static Prediction productionPrediction(Race race, String[] fields, PredictionValues values, List<String> teams) {
        return new Prediction(new PredictionId(UUID.randomUUID()), LEAGUE,
                new LeagueParticipantId(UUID.fromString(fields[2])), race.id(), values, teams);
    }

    private static List<Prediction> productionPredictions(Race race, String[] fields, Prediction selected) {
        var predictions = new ArrayList<Prediction>();
        predictions.add(selected);
        names(fields[16]).forEach(lead -> predictions.add(prediction(race, LEAGUE,
                new PredictionValues(null, null, null, null, new BigDecimal(lead)))));
        return predictions;
    }

    private static RaceLineup productionLineup(Race race, String[] fields) {
        return new RaceLineup(race.id(), Arrays.stream(fields[15].split(";")).map(pair -> team(drivers(pair))).toList());
    }

    private static void assertProductionPoints(Race race, RaceResult result, PredictionConditions conditions,
                                               RaceLineup lineup, List<Prediction> predictions, PredictionPoints expected) {
        assertThat(PredictionPointsCalculator.calculate(race, result, conditions, lineup, predictions))
                .filteredOn(points -> points.predictionId().equals(expected.predictionId())).containsExactly(expected);
    }

    private static SafetyCarsCount safetyCars(String value) {
        return switch (value) {
            case "Zero" -> SafetyCarsCount.ZERO;
            case "One" -> SafetyCarsCount.ONE;
            case "Two" -> SafetyCarsCount.TWO;
            case "ThreePlus" -> SafetyCarsCount.THREE_PLUS;
            default -> throw new IllegalArgumentException(value);
        };
    }

    private static List<String> names(String value) {
        return value.isEmpty() ? List.of() : List.of(value.split(","));
    }

    private static List<Driver> drivers(String value) {
        return names(value).stream().map(Driver::new).toList();
    }

    private static List<Driver> numberedDrivers(String value) {
        return value.equals("nobody") ? List.of() : Arrays.stream(value.split(";")).map(Integer::parseInt)
                .map(PredictionPointsCalculatorTest::driver).toList();
    }

    private static Driver driver(int index) {
        return new Driver("Driver " + index);
    }

    private static List<RaceClassificationEntry> classification(List<Driver> dnfs) {
        return IntStream.rangeClosed(1, 22).mapToObj(index -> new RaceClassificationEntry(driver(index),
                dnfs.contains(driver(index)) ? RaceDriverStatus.DNF : RaceDriverStatus.FINISHED)).toList();
    }

    private static List<RaceClassificationEntry> classificationWithNonFinishers() {
        return IntStream.rangeClosed(1, 22).mapToObj(index -> new RaceClassificationEntry(driver(index), switch (index) {
            case 18, 19, 20 -> RaceDriverStatus.DNF;
            case 21 -> RaceDriverStatus.DNS;
            case 22 -> RaceDriverStatus.DQ;
            default -> RaceDriverStatus.FINISHED;
        })).toList();
    }

    private static Team team(List<Driver> drivers) {
        return new Team(new TeamBranding(drivers.getFirst().name(), "T", "#000000", "#FFFFFF"), drivers);
    }

    private static Race race(int season, boolean sprint) {
        return race(new RaceId(UUID.randomUUID()), season, sprint, "Grand Prix");
    }

    private static Race race(RaceId id, int season, boolean sprint, String name) {
        var closeAt = Instant.parse(season + "-03-07T12:00:00Z");
        return new Race(id, season, 1, sprint ? RaceType.SPRINT : RaceType.RACE, name, RaceStatus.CLOSED,
                closeAt.minusSeconds(86400), closeAt);
    }

    private static RaceResult result(Race race, RaceResultValues values) {
        return new RaceResult(race.id(), new RulesVersion(race.season() == 2027 ? "2027-v1" : String.valueOf(race.season())), values, null);
    }

    private static Prediction prediction(Race race, LeagueId leagueId, PredictionValues values) {
        return new Prediction(new PredictionId(UUID.randomUUID()), leagueId, new LeagueParticipantId(UUID.randomUUID()),
                race.id(), values, null);
    }

    private static PredictionValues lead(String value) {
        return new PredictionValues(null, null, null, null, value == null ? null : new BigDecimal(value));
    }
}
