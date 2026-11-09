package com.xdd.f1.domain.prediction;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.result.RaceClassificationEntry;
import com.xdd.f1.domain.race.result.RaceDriverStatus;
import com.xdd.f1.domain.race.result.RaceResult;
import com.xdd.f1.domain.race.result.RaceResultValues;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class PredictionPointsCalculator {
    private static final int[] TENTH_PLACE_POINTS = {0, 1, 2, 4, 6, 8, 10, 12, 15, 18, 25, 18, 15, 12, 10, 8, 6, 4, 2, 1, 1};
    private static final int[] POSITION_POINTS = {10, 7, 5, 3, 2, 1};
    private static final List<BigDecimal> LEAD_THRESHOLDS = Stream.of("0.5", "2", "3.5", "5", "7")
            .map(BigDecimal::new).toList();

    private PredictionPointsCalculator() {
    }

    public static List<PredictionPoints> calculate(Race race, RaceResult result, PredictionConditions conditions,
                                                  RaceLineup lineup, List<Prediction> predictions) {
        validate(race, result, conditions, lineup, predictions);
        return switch (result.rulesVersion().value()) {
            case "2023" -> calculate2023(result, predictions);
            case "2024" -> calculate2024(result, lineup, predictions);
            case "2025" -> calculate2025(race, result, lineup, predictions);
            case "2026" -> calculate2026(race, result, conditions, predictions);
            case "2027-v1" -> calculate2027(race, result, conditions, predictions);
            default -> throw new DomainValidationException("Prediction rules are not defined for version " + result.rulesVersion().value());
        };
    }

    private static List<PredictionPoints> calculate2023(RaceResult result, List<Prediction> predictions) {
        var positions = positions(result.result());
        var dnfs = dnfs(result.result());
        return predictions.stream().map(prediction -> {
            var picks = prediction.values();
            var tenth = tenthPlacePoints(picks.tenthPlacePickedDriver(), positions);
            var dnf = firstDnfPoints(picks.dnfPrediction(), dnfs);
            return points(prediction, result.rulesVersion(), false, tenth, dnf, null, null, null, null);
        }).toList();
    }

    private static List<PredictionPoints> calculate2024(RaceResult result, RaceLineup lineup, List<Prediction> predictions) {
        var values = result.result();
        var positions = positions(values);
        var dnfs = dnfs(values);
        var closestLeads = closestLeads(values, predictions);
        var winners = teamWinners(lineup, positions, predictions);
        return predictions.stream().map(prediction -> {
            var picks = prediction.values();
            var tenth = tenthPlacePoints(picks.tenthPlacePickedDriver(), positions);
            var dnf = dnfPoints(picks.dnfPrediction(), dnfs);
            var safety = safetyCarsPoints(picks.safetyCarsPrediction(), values.safetyCars());
            var lead = relativeLeadPoints(picks.firstPlaceLeadPrediction(), values.firstPlaceLead(), closestLeads.get(prediction.leagueId()));
            var team = teamMatesPoints(prediction.teamsPickedDrivers(), winners);
            return points(prediction, result.rulesVersion(), false, tenth, dnf, safety, lead, null, team);
        }).toList();
    }

    private static List<PredictionPoints> calculate2025(Race race, RaceResult result, RaceLineup lineup, List<Prediction> predictions) {
        var values = result.result();
        var positions = positions(values);
        var dnfs = dnfs(values);
        var closestLeads = closestLeads(values, predictions);
        var winners = teamWinners(lineup, positions, predictions);
        return predictions.stream().map(prediction -> {
            var picks = prediction.values();
            var tenth = tenthPlacePoints(picks.tenthPlacePickedDriver(), positions);
            var dnf = dnfPoints(picks.dnfPrediction(), dnfs);
            var safety = safetyCarsPoints(picks.safetyCarsPrediction(), values.safetyCars());
            var lead = relativeLeadPoints(picks.firstPlaceLeadPrediction(), values.firstPlaceLead(), closestLeads.get(prediction.leagueId()));
            var team = teamMatesPoints(prediction.teamsPickedDrivers(), winners);
            return points(prediction, result.rulesVersion(), race.type() == RaceType.SPRINT, tenth, dnf, safety, lead, null, team);
        }).toList();
    }

    private static List<PredictionPoints> calculate2026(Race race, RaceResult result, PredictionConditions conditions, List<Prediction> predictions) {
        var values = result.result();
        var positions = positions(values);
        var dnfs = dnfs(values);
        var closestLeads = closestLeads(values, predictions);
        return predictions.stream().map(prediction -> {
            var picks = prediction.values();
            var tenth = tenthPlacePoints(picks.tenthPlacePickedDriver(), positions);
            var dnf = dnfPoints(picks.dnfPrediction(), dnfs);
            var safety = safetyCarsPoints(picks.safetyCarsPrediction(), values.safetyCars());
            var lead = relativeLeadPoints(picks.firstPlaceLeadPrediction(), values.firstPlaceLead(), closestLeads.get(prediction.leagueId()));
            var position = positionPoints(picks.driverPositionPrediction(), conditions, positions);
            return points(prediction, result.rulesVersion(), race.type() == RaceType.SPRINT, tenth, dnf, safety, lead, position, null);
        }).toList();
    }

    private static List<PredictionPoints> calculate2027(Race race, RaceResult result, PredictionConditions conditions, List<Prediction> predictions) {
        var values = result.result();
        var positions = positions(values);
        var dnfs = dnfs(values);
        return predictions.stream().map(prediction -> {
            var picks = prediction.values();
            var tenth = tenthPlacePoints(picks.tenthPlacePickedDriver(), positions);
            var dnf = dnfPoints(picks.dnfPrediction(), dnfs);
            var safety = safetyCarsPoints(picks.safetyCarsPrediction(), values.safetyCars());
            var lead = absoluteLeadPoints(picks.firstPlaceLeadPrediction(), values.firstPlaceLead());
            var position = positionPoints(picks.driverPositionPrediction(), conditions, positions);
            return points(prediction, result.rulesVersion(), race.type() == RaceType.SPRINT, tenth, dnf, safety, lead, position, null);
        }).toList();
    }

    private static PredictionPoints points(Prediction prediction, RulesVersion version, boolean reducedSprint,
                                            Integer tenth, Integer dnf, Integer safety, Integer lead, Integer position, Integer team) {
        var total = Stream.of(tenth, dnf, safety, lead, position, team).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        if (reducedSprint) {
            total = total * 3 / 10;
        }
        return new PredictionPoints(prediction.id(), version, total, tenth, dnf, safety, lead, position, team);
    }

    private static void validate(Race race, RaceResult result, PredictionConditions conditions,
                                 RaceLineup lineup, List<Prediction> predictions) {
        var season = seasonFor(result.rulesVersion());
        if (race.season() != season) {
            throw new DomainValidationException("Rules version " + result.rulesVersion().value() + " does not apply to season " + race.season());
        }
        if (!race.id().equals(result.raceId())
                || conditions != null && !race.id().equals(conditions.raceId())
                || lineup != null && !race.id().equals(lineup.raceId())
                || predictions.stream().anyMatch(prediction -> !race.id().equals(prediction.raceId()))) {
            throw new DomainValidationException("Calculation data must belong to the same race");
        }
        if (predictions.stream().map(Prediction::id).distinct().count() != predictions.size()) {
            throw new DomainValidationException("Calculation must not include duplicate predictions");
        }
        if (result.result() == null) {
            throw new DomainValidationException("Race result must be provided for calculating points");
        }
    }

    private static Map<Driver, Integer> positions(RaceResultValues values) {
        if (values.classification() == null) {
            return null;
        }
        return IntStream.range(0, values.classification().size()).boxed()
                .collect(Collectors.toMap(index -> values.classification().get(index).driver(), index -> index + 1));
    }

    private static List<Driver> dnfs(RaceResultValues values) {
        if (values.classification() == null) {
            return null;
        }
        return values.classification().reversed().stream().filter(entry -> entry.status() == RaceDriverStatus.DNF)
                .map(RaceClassificationEntry::driver).toList();
    }

    private static int seasonFor(RulesVersion version) {
        return switch (version.value()) {
            case "2023" -> 2023;
            case "2024" -> 2024;
            case "2025" -> 2025;
            case "2026" -> 2026;
            case "2027-v1" -> 2027;
            default -> throw new DomainValidationException("Prediction rules are not defined for version " + version.value());
        };
    }

    private static Integer tenthPlacePoints(Driver selected, Map<Driver, Integer> positions) {
        if (selected == null || positions == null) {
            return null;
        }
        var position = positions.getOrDefault(selected, 0);
        return position < TENTH_PLACE_POINTS.length ? TENTH_PLACE_POINTS[position] : 0;
    }

    private static Integer firstDnfPoints(DnfPrediction prediction, List<Driver> dnfs) {
        if (prediction == null || dnfs == null) {
            return null;
        }
        return !dnfs.isEmpty() && !prediction.dnfPickedDrivers().isEmpty()
                && dnfs.getFirst().equals(prediction.dnfPickedDrivers().getFirst()) ? 5 : 0;
    }

    private static Integer dnfPoints(DnfPrediction prediction, List<Driver> dnfs) {
        if (prediction == null || dnfs == null) {
            return null;
        }
        if (prediction.dnfPickedDrivers().isEmpty()) {
            return dnfs.isEmpty() ? 10 : 0;
        }
        return (int) prediction.dnfPickedDrivers().stream().filter(dnfs::contains).count() * 2;
    }

    private static Integer safetyCarsPoints(SafetyCarsCount prediction, Integer actual) {
        if (prediction == null || actual == null) {
            return null;
        }
        var category = switch (actual) {
            case 0 -> SafetyCarsCount.ZERO;
            case 1 -> SafetyCarsCount.ONE;
            case 2 -> SafetyCarsCount.TWO;
            default -> SafetyCarsCount.THREE_PLUS;
        };
        return prediction == category ? 5 : 0;
    }

    private static Map<LeagueId, BigDecimal> closestLeads(RaceResultValues result, List<Prediction> predictions) {
        var closest = new HashMap<LeagueId, BigDecimal>();
        if (result.firstPlaceLead() != null) {
            predictions.stream().filter(prediction -> prediction.values().firstPlaceLeadPrediction() != null)
                    .forEach(prediction -> closest.merge(prediction.leagueId(),
                            prediction.values().firstPlaceLeadPrediction().subtract(result.firstPlaceLead()).abs(), BigDecimal::min));
        }
        return closest;
    }

    private static Integer relativeLeadPoints(BigDecimal prediction, BigDecimal actual, BigDecimal closest) {
        if (prediction == null || actual == null) {
            return null;
        }
        return prediction.subtract(actual).abs().compareTo(closest) == 0 ? 5 : 0;
    }

    private static Integer absoluteLeadPoints(BigDecimal prediction, BigDecimal actual) {
        if (prediction == null || actual == null) {
            return null;
        }
        var difference = prediction.subtract(actual).abs();
        for (int index = 0; index < LEAD_THRESHOLDS.size(); index++) {
            if (difference.compareTo(LEAD_THRESHOLDS.get(index)) <= 0) {
                return 5 - index;
            }
        }
        return 0;
    }

    private static Integer positionPoints(Integer prediction, PredictionConditions conditions, Map<Driver, Integer> positions) {
        if (prediction == null || conditions == null || conditions.positionPredictionDriver() == null || positions == null) {
            return null;
        }
        var actual = positions.get(conditions.positionPredictionDriver());
        if (actual == null) {
            throw new DomainValidationException("The position prediction driver must be present in the classification");
        }
        var difference = Math.abs(prediction - actual);
        return difference < POSITION_POINTS.length ? POSITION_POINTS[difference] : 0;
    }

    private static Set<String> teamWinners(RaceLineup lineup, Map<Driver, Integer> positions, List<Prediction> predictions) {
        if (positions == null || predictions.stream().noneMatch(prediction -> prediction.teamsPickedDrivers() != null)) {
            return null;
        }
        if (lineup == null) {
            throw new DomainValidationException("The race lineup is required for team predictions");
        }
        return lineup.teams().stream().map(team -> team.drivers().stream().filter(positions::containsKey)
                        .min(Comparator.comparingInt(positions::get)))
                .flatMap(Optional::stream).map(Driver::name).collect(Collectors.toSet());
    }

    private static Integer teamMatesPoints(List<String> selected, Set<String> winners) {
        if (selected == null || winners == null) {
            return null;
        }
        return (int) selected.stream().distinct().filter(winners::contains).count();
    }
}
