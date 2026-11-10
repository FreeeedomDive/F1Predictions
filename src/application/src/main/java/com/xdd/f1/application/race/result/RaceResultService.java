package com.xdd.f1.application.race.result;

import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.prediction.PredictionConditionsRepository;
import com.xdd.f1.application.prediction.PredictionPointsRepository;
import com.xdd.f1.application.prediction.PredictionRepository;
import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.lineup.RaceLineupNotFoundException;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.contract.race.RaceFinishedEvent;
import com.xdd.f1.contract.race.RaceResultUpdatedEvent;
import com.xdd.f1.contract.race.result.*;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.*;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.result.RaceClassificationEntry;
import com.xdd.f1.domain.race.result.RaceResult;
import com.xdd.f1.domain.race.result.RaceResultValues;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RaceResultService {
    private final RaceResultRepository results;
    private final PredictionPointsRepository points;
    private final PredictionRepository predictions;
    private final PredictionConditionsRepository conditions;
    private final RaceRepository races;
    private final RaceLineupRepository lineups;
    private final LeagueParticipantRepository participants;
    private final TransactionLock lock;
    private final ApplicationEventPublisher events;

    public RaceResultService(RaceResultRepository results, PredictionPointsRepository points, PredictionRepository predictions,
                             PredictionConditionsRepository conditions, RaceRepository races, RaceLineupRepository lineups,
                             LeagueParticipantRepository participants, TransactionLock lock, ApplicationEventPublisher events) {
        this.results = results;
        this.points = points;
        this.predictions = predictions;
        this.conditions = conditions;
        this.races = races;
        this.lineups = lineups;
        this.participants = participants;
        this.lock = lock;
        this.events = events;
    }

    @Transactional
    public RaceResult saveDraft(RaceId raceId, RaceResultValues values) {
        Objects.requireNonNull(values, "Race result values must not be null");
        var race = getLockedRace(raceId);
        if (race.status() == RaceStatus.SCHEDULED) {
            throw new RaceResultOperationNotAllowedException(raceId, race.status(), "save a result draft");
        }
        var previous = results.findByRaceId(raceId);
        var rules = rulesFor(race, previous);
        checkPointsRules(raceId, rules, points.findAllByRaceId(raceId));
        var lineup = getLineup(raceId);
        validateDrivers(values, lineup);
        if (previous.isPresent() && (Objects.equals(previous.get().draft(), values)
                || previous.get().draft() == null && Objects.equals(previous.get().result(), values))) {
            return previous.get();
        }
        var updated = new RaceResult(raceId, rules, previous.map(RaceResult::result).orElse(null), values);
        if (previous.isEmpty()) {
            results.create(updated);
        } else {
            results.update(updated);
        }
        return updated;
    }

    @Transactional
    public RaceResult finalizeResult(RaceId raceId) {
        var race = getLockedRace(raceId);
        if (race.status() != RaceStatus.CLOSED && race.status() != RaceStatus.FINISHED) {
            throw new RaceResultOperationNotAllowedException(raceId, race.status(), "finalize a result");
        }
        var previous = results.findByRaceId(raceId).orElseThrow(() -> new RaceResultNotFoundException(raceId));
        var rules = rulesFor(race, Optional.of(previous));
        var existingPoints = points.findAllByRaceId(raceId);
        checkPointsRules(raceId, rules, existingPoints);
        var values = previous.draft() != null ? previous.draft() : previous.result();
        var savedPredictions = predictions.findAllByRaceId(raceId);
        var raceConditions = conditions.findByRaceId(raceId).orElse(null);
        var lineup = getLineup(raceId);
        validateFinalResult(raceId, values, lineup, raceConditions, savedPredictions);
        var updated = new RaceResult(raceId, rules, values, null);
        var calculated = PredictionPointsCalculator.calculate(race, updated, raceConditions, lineup, savedPredictions);
        if (!updated.equals(previous)) {
            results.update(updated);
        }
        var pointsChanged = savePoints(calculated, existingPoints);
        var finished = race.finish();
        if (!finished.equals(race)) {
            races.update(finished);
        }
        if (!Objects.equals(previous.result(), values) || pointsChanged || race.status() != RaceStatus.FINISHED) {
            publishResults(race, updated, savedPredictions, calculated);
        }
        return updated;
    }

    private Race getLockedRace(RaceId raceId) {
        lock.acquire("race:" + raceId.value());
        return races.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
    }

    private RaceLineup getLineup(RaceId raceId) {
        return lineups.findByRaceId(raceId).orElseThrow(() -> new RaceLineupNotFoundException(raceId));
    }

    private RulesVersion rulesFor(Race race, Optional<RaceResult> previous) {
        var expected = switch (race.season()) {
            case 2023, 2024, 2025, 2026 -> new RulesVersion(String.valueOf(race.season()));
            case 2027 -> new RulesVersion("2027-v1");
            default -> throw new RaceResultRulesException(race.id(), new RulesVersion(String.valueOf(race.season())));
        };
        var version = previous.map(RaceResult::rulesVersion).orElse(expected);
        if (!expected.equals(version)) {
            throw new RaceResultRulesException(race.id(), version);
        }
        return version;
    }

    private void checkPointsRules(RaceId raceId, RulesVersion version, List<PredictionPoints> existing) {
        existing.stream().filter(saved -> !saved.rulesVersion().equals(version)).findFirst()
                .ifPresent(saved -> {
                    throw new RaceResultRulesException(raceId, saved.rulesVersion());
                });
    }

    private void validateDrivers(RaceResultValues values, RaceLineup lineup) {
        if (values.classification() == null) {
            return;
        }
        var drivers = lineup.teams().stream().flatMap(team -> team.drivers().stream()).collect(Collectors.toSet());
        for (var entry : values.classification()) {
            if (!drivers.contains(entry.driver())) {
                throw new DomainValidationException("Driver does not belong to this race lineup: " + entry.driver().name());
            }
        }
    }

    private void validateFinalResult(RaceId raceId, RaceResultValues values, RaceLineup lineup,
                                     PredictionConditions raceConditions, List<Prediction> savedPredictions) {
        if (values == null || values.classification() == null && values.safetyCars() == null && values.firstPlaceLead() == null) {
            throw new RaceResultIncompleteException(raceId, "No result categories have been provided");
        }
        validateDrivers(values, lineup);
        if (values.classification() != null) {
            var drivers = lineup.teams().stream().flatMap(team -> team.drivers().stream()).collect(Collectors.toSet());
            var classified = values.classification().stream().map(RaceClassificationEntry::driver).collect(Collectors.toSet());
            if (!classified.equals(drivers)) {
                throw new RaceResultIncompleteException(raceId, "The classification must include every driver in the race lineup");
            }
        }
        for (var prediction : savedPredictions) {
            var picks = prediction.values();
            if (values.classification() == null && (picks.tenthPlacePickedDriver() != null || picks.dnfPrediction() != null
                    || picks.driverPositionPrediction() != null || prediction.teamsPickedDrivers() != null)) {
                throw new RaceResultIncompleteException(raceId, "The classification is required for saved predictions");
            }
            if (values.safetyCars() == null && picks.safetyCarsPrediction() != null) {
                throw new RaceResultIncompleteException(raceId, "The incident count is required for saved predictions");
            }
            if (values.firstPlaceLead() == null && picks.firstPlaceLeadPrediction() != null) {
                throw new RaceResultIncompleteException(raceId, "The winner gap is required for saved predictions");
            }
            if (picks.driverPositionPrediction() != null && (raceConditions == null || raceConditions.positionPredictionDriver() == null)) {
                throw new RaceResultIncompleteException(raceId, "The position prediction driver is missing");
            }
        }
    }

    private boolean savePoints(List<PredictionPoints> calculated, List<PredictionPoints> existing) {
        var byId = existing.stream().collect(Collectors.toMap(PredictionPoints::predictionId, value -> value));
        var changed = false;
        for (var value : calculated) {
            var previous = byId.get(value.predictionId());
            if (previous == null) {
                points.create(value);
                changed = true;
            } else if (!previous.equals(value)) {
                points.update(value);
                changed = true;
            }
        }
        return changed;
    }

    private void publishResults(Race race, RaceResult result, List<Prediction> savedPredictions, List<PredictionPoints> calculated) {
        var names = participants.findNamesByIds(savedPredictions.stream().map(Prediction::participantId).distinct().toList());
        var byId = calculated.stream().collect(Collectors.toMap(PredictionPoints::predictionId, value -> value));
        var byLeague = savedPredictions.stream().collect(Collectors.groupingBy(Prediction::leagueId));
        var classification = result.result().classification() == null ? null : result.result().classification().stream()
                .map(entry -> new RaceClassificationEntryDto(entry.driver().name(), RaceDriverStatusDto.valueOf(entry.status().name()))).toList();
        var values = new RaceResultValuesDto(classification, result.result().safetyCars(), result.result().firstPlaceLead());
        byLeague.forEach((leagueId, leaguePredictions) -> {
            var leaguePoints = leaguePredictions.stream().map(prediction -> {
                var name = names.get(prediction.participantId());
                if (name == null) {
                    throw new LeagueParticipantNotFoundException(prediction.participantId());
                }
                return new RaceParticipantPointsDto(prediction.participantId().value(), name, byId.get(prediction.id()).totalPoints());
            }).sorted(Comparator.comparingInt(RaceParticipantPointsDto::totalPoints).reversed()
                    .thenComparing(RaceParticipantPointsDto::participantName).thenComparing(RaceParticipantPointsDto::participantId)).toList();
            if (race.status() == RaceStatus.FINISHED) {
                events.publishEvent(new RaceResultUpdatedEvent(leagueId.value(), race.id().value(), race.name(), race.type().name(),
                        race.season(), result.rulesVersion().value(), values, leaguePoints));
            } else {
                events.publishEvent(new RaceFinishedEvent(leagueId.value(), race.id().value(), race.name(), race.type().name(),
                        race.season(), result.rulesVersion().value(), values, leaguePoints));
            }
        });
    }
}
