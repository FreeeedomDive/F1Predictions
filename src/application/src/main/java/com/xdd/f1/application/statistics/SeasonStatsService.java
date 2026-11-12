package com.xdd.f1.application.statistics;

import com.xdd.f1.application.league.LeagueAccessGuard;
import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.prediction.PredictionRepository;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.result.RaceResultRepository;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.prediction.PredictionPointsRules;
import com.xdd.f1.domain.prediction.SafetyCarsCount;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.result.RaceDriverStatus;
import com.xdd.f1.domain.race.result.RaceResult;
import com.xdd.f1.domain.race.result.RaceResultValues;
import com.xdd.f1.domain.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeasonStatsService {
    private static final int TOP_COUNT = 5;

    private final LeagueAccessGuard access;
    private final LeagueRepository leagues;
    private final RaceRepository races;
    private final PredictionRepository predictions;
    private final RaceResultRepository results;

    public SeasonStatsService(LeagueAccessGuard access, LeagueRepository leagues, RaceRepository races,
                              PredictionRepository predictions, RaceResultRepository results) {
        this.access = access;
        this.leagues = leagues;
        this.races = races;
        this.predictions = predictions;
        this.results = results;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SeasonStats getSeasonStats(UserId userId, LeagueId leagueId, int season) {
        access.checkAccess(userId, leagueId);
        var league = leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        if (season < league.settings().firstSeason()) {
            return SeasonStats.empty();
        }

        var calendar = races.findAllBySeason(season);
        var racesById = calendar.stream().collect(Collectors.toMap(Race::id, Function.identity()));
        var publishedByRace = results.findAllPublishedBySeason(season).stream()
                .filter(saved -> racesById.containsKey(saved.raceId()))
                .filter(saved -> racesById.get(saved.raceId()).status() == RaceStatus.FINISHED)
                .collect(Collectors.toMap(RaceResult::raceId, Function.identity()));
        var allPredictions = predictions.findSeasonStatistics(leagueId, season);
        var predictionRaceIds = allPredictions.stream()
                .filter(saved -> publishedByRace.containsKey(saved.prediction().raceId()))
                .map(saved -> saved.prediction().raceId())
                .collect(Collectors.toSet());
        var visibleRaceIds = calendar.stream()
                .filter(race -> league.settings().sprintsEnabled() || race.type() != RaceType.SPRINT)
                .map(Race::id)
                .collect(Collectors.toSet());
        var statsRaces = calendar.stream()
                .filter(race -> race.status() == RaceStatus.FINISHED && publishedByRace.containsKey(race.id()))
                .filter(race -> visibleRaceIds.contains(race.id()) || predictionRaceIds.contains(race.id()))
                .map(race -> new StatsRace(race, publishedByRace.get(race.id()).result()))
                .toList();
        var statsRaceIds = statsRaces.stream().map(saved -> saved.race().id()).collect(Collectors.toSet());
        var statsPredictions = allPredictions.stream()
                .filter(saved -> statsRaceIds.contains(saved.prediction().raceId()))
                .toList();
        var racesByStatsId = statsRaces.stream().collect(Collectors.toMap(saved -> saved.race().id(), Function.identity()));
        var dnfsByRace = dnfsByRace(statsRaces);

        return new SeasonStats(
                tenthPlacePointsRating(statsPredictions),
                mostPickedForTenthPlace(statsPredictions),
                tenthPickedButDnfed(statsPredictions, dnfsByRace),
                driverOwnTenthPlacePoints(statsRaces),
                tenthPlacePredictionEfficiency(statsPredictions),
                mostDnfDrivers(dnfsByRace),
                mostPickedForDnf(statsPredictions),
                tenthPlaceDnfAntiRating(statsPredictions, dnfsByRace),
                closestLeadGapPredictions(statsPredictions, racesByStatsId),
                safetyCarPickCounts(statsPredictions),
                safetyCarActualCounts(statsRaces),
                safetyCarCorrectGuesses(statsPredictions, racesByStatsId)
        );
    }

    private List<SeasonDriverStats> tenthPlacePointsRating(List<SeasonStatsPrediction> predictions) {
        var scores = new LinkedHashMap<String, Integer>();
        predictions.forEach(saved -> {
            var selected = saved.prediction().values().tenthPlacePickedDriver();
            if (selected != null) {
                scores.merge(selected.name(), saved.tenthPlacePoints() == null ? 0 : saved.tenthPlacePoints(), Integer::sum);
            }
        });
        return topDrivers(scores);
    }

    private List<SeasonDriverStats> mostPickedForTenthPlace(List<SeasonStatsPrediction> predictions) {
        var scores = new LinkedHashMap<String, Integer>();
        predictions.forEach(saved -> {
            var selected = saved.prediction().values().tenthPlacePickedDriver();
            if (selected != null) {
                scores.merge(selected.name(), 1, Integer::sum);
            }
        });
        return topDrivers(scores);
    }

    private List<SeasonDriverStats> tenthPickedButDnfed(List<SeasonStatsPrediction> predictions,
                                                         Map<RaceId, Set<Driver>> dnfsByRace) {
        var scores = new LinkedHashMap<String, Integer>();
        predictions.forEach(saved -> {
            var prediction = saved.prediction();
            var selected = prediction.values().tenthPlacePickedDriver();
            if (selected != null && dnfsByRace.getOrDefault(prediction.raceId(), Set.of()).contains(selected)) {
                scores.merge(selected.name(), 1, Integer::sum);
            }
        });
        return topDrivers(scores);
    }

    private List<SeasonDriverStats> driverOwnTenthPlacePoints(List<StatsRace> races) {
        var scores = new LinkedHashMap<String, Integer>();
        races.forEach(saved -> {
            var classification = saved.result().classification();
            if (classification != null) {
                for (var index = 0; index < classification.size(); index++) {
                    scores.merge(classification.get(index).driver().name(), PredictionPointsRules.tenthPlacePoints(index + 1), Integer::sum);
                }
            }
        });
        return topDrivers(scores);
    }

    private List<SeasonDriverStats> tenthPlacePredictionEfficiency(List<SeasonStatsPrediction> predictions) {
        var totals = new LinkedHashMap<String, int[]>();
        predictions.forEach(saved -> {
            var selected = saved.prediction().values().tenthPlacePickedDriver();
            if (selected != null) {
                var total = totals.computeIfAbsent(selected.name(), ignored -> new int[2]);
                total[0] += saved.tenthPlacePoints() == null ? 0 : saved.tenthPlacePoints();
                total[1]++;
            }
        });
        var averages = new LinkedHashMap<String, Integer>();
        totals.forEach((driver, total) -> averages.put(driver, total[0] / total[1]));
        return topDrivers(averages);
    }

    private List<SeasonDriverStats> mostDnfDrivers(Map<RaceId, Set<Driver>> dnfsByRace) {
        var scores = new LinkedHashMap<String, Integer>();
        dnfsByRace.values().forEach(dnfs -> dnfs.forEach(driver -> scores.merge(driver.name(), 1, Integer::sum)));
        return topDrivers(scores);
    }

    private List<SeasonDriverStats> mostPickedForDnf(List<SeasonStatsPrediction> predictions) {
        var scores = new LinkedHashMap<String, Integer>();
        predictions.forEach(saved -> {
            var prediction = saved.prediction().values().dnfPrediction();
            if (prediction != null) {
                prediction.dnfPickedDrivers().forEach(driver -> scores.merge(driver.name(), 1, Integer::sum));
            }
        });
        return topDrivers(scores);
    }

    private List<SeasonParticipantStats> tenthPlaceDnfAntiRating(List<SeasonStatsPrediction> predictions,
                                                                  Map<RaceId, Set<Driver>> dnfsByRace) {
        var scores = new LinkedHashMap<LeagueParticipantId, Integer>();
        var names = new HashMap<LeagueParticipantId, String>();
        predictions.forEach(saved -> {
            var prediction = saved.prediction();
            var selected = prediction.values().tenthPlacePickedDriver();
            if (selected != null && dnfsByRace.getOrDefault(prediction.raceId(), Set.of()).contains(selected)) {
                scores.merge(prediction.participantId(), 1, Integer::sum);
                names.putIfAbsent(prediction.participantId(), saved.participantName());
            }
        });
        return scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_COUNT)
                .map(entry -> new SeasonParticipantStats(entry.getKey(), names.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    private List<SeasonLeadGapPredictionStats> closestLeadGapPredictions(List<SeasonStatsPrediction> predictions,
                                                                          Map<RaceId, StatsRace> racesById) {
        return predictions.stream().map(saved -> {
                    var prediction = saved.prediction();
                    var race = racesById.get(prediction.raceId());
                    var predicted = prediction.values().firstPlaceLeadPrediction();
                    var actual = race.result().firstPlaceLead();
                    if (predicted == null || actual == null) {
                        return null;
                    }
                    return new SeasonLeadGapPredictionStats(
                            prediction.participantId(), saved.participantName(), race.race().name(),
                            predicted, actual, predicted.subtract(actual).abs()
                    );
                }).filter(Objects::nonNull)
                .sorted(Comparator.comparing(SeasonLeadGapPredictionStats::difference))
                .limit(TOP_COUNT)
                .toList();
    }

    private List<SeasonSafetyCarStats> safetyCarPickCounts(List<SeasonStatsPrediction> predictions) {
        var scores = new LinkedHashMap<SafetyCarsCount, Integer>();
        predictions.forEach(saved -> {
            var selected = saved.prediction().values().safetyCarsPrediction();
            if (selected != null) {
                scores.merge(selected, 1, Integer::sum);
            }
        });
        return safetyCarStats(scores);
    }

    private List<SeasonSafetyCarStats> safetyCarActualCounts(List<StatsRace> races) {
        var scores = new LinkedHashMap<SafetyCarsCount, Integer>();
        races.forEach(saved -> {
            var actual = saved.result().safetyCars();
            if (actual != null) {
                scores.merge(safetyCarsCategory(actual), 1, Integer::sum);
            }
        });
        return safetyCarStats(scores);
    }

    private List<SeasonSafetyCarStats> safetyCarCorrectGuesses(List<SeasonStatsPrediction> predictions,
                                                                Map<RaceId, StatsRace> racesById) {
        var scores = new LinkedHashMap<SafetyCarsCount, Integer>();
        predictions.forEach(saved -> {
            var prediction = saved.prediction();
            var selected = prediction.values().safetyCarsPrediction();
            var actual = racesById.get(prediction.raceId()).result().safetyCars();
            if (selected != null && actual != null && selected == safetyCarsCategory(actual)) {
                scores.merge(selected, 1, Integer::sum);
            }
        });
        return safetyCarStats(scores);
    }

    private Map<RaceId, Set<Driver>> dnfsByRace(List<StatsRace> races) {
        var dnfs = new LinkedHashMap<RaceId, Set<Driver>>();
        races.forEach(saved -> {
            var classification = saved.result().classification();
            if (classification != null) {
                dnfs.put(saved.race().id(), classification.stream()
                        .filter(entry -> entry.status() == RaceDriverStatus.DNF)
                        .map(entry -> entry.driver())
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
            }
        });
        return dnfs;
    }

    private List<SeasonDriverStats> topDrivers(Map<String, Integer> scores) {
        return scores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_COUNT)
                .map(entry -> new SeasonDriverStats(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<SeasonSafetyCarStats> safetyCarStats(Map<SafetyCarsCount, Integer> scores) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(entry -> new SeasonSafetyCarStats(entry.getKey(), entry.getValue()))
                .toList();
    }

    private SafetyCarsCount safetyCarsCategory(int actual) {
        return switch (actual) {
            case 0 -> SafetyCarsCount.ZERO;
            case 1 -> SafetyCarsCount.ONE;
            case 2 -> SafetyCarsCount.TWO;
            default -> SafetyCarsCount.THREE_PLUS;
        };
    }

    private record StatsRace(Race race, RaceResultValues result) {
    }
}
