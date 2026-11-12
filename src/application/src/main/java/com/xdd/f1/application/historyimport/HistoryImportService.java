package com.xdd.f1.application.historyimport;

import com.xdd.f1.application.league.*;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.prediction.*;
import com.xdd.f1.application.race.RaceNotFoundException;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.lineup.RaceLineupNotFoundException;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.application.race.result.RaceResultRepository;
import com.xdd.f1.application.standings.SeasonPodiumEntry;
import com.xdd.f1.application.standings.SeasonPodiumRepository;
import com.xdd.f1.application.standings.SeasonStandingsService;
import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.*;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.*;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.result.RaceClassificationEntry;
import com.xdd.f1.domain.race.result.RaceResult;
import com.xdd.f1.domain.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xdd.f1.application.historyimport.HistoryImport.*;

@Service
public class HistoryImportService {
    private final LeagueRepository leagues;
    private final UserRepository users;
    private final LeagueParticipantRepository participants;
    private final RaceRepository races;
    private final RaceLineupRepository lineups;
    private final PredictionConditionsRepository conditions;
    private final PredictionRepository predictions;
    private final RaceResultRepository results;
    private final PredictionPointsRepository points;
    private final SeasonPodiumRepository podiums;
    private final SeasonStandingsService standings;
    private final TransactionLock lock;

    public HistoryImportService(LeagueRepository leagues, UserRepository users, LeagueParticipantRepository participants,
                                RaceRepository races, RaceLineupRepository lineups, PredictionConditionsRepository conditions,
                                PredictionRepository predictions, RaceResultRepository results, PredictionPointsRepository points,
                                SeasonPodiumRepository podiums, SeasonStandingsService standings, TransactionLock lock) {
        this.leagues = leagues;
        this.users = users;
        this.participants = participants;
        this.races = races;
        this.lineups = lineups;
        this.conditions = conditions;
        this.predictions = predictions;
        this.results = results;
        this.points = points;
        this.podiums = podiums;
        this.standings = standings;
        this.lock = lock;
    }

    @Transactional(readOnly = true)
    public League getLeague(LeagueId leagueId) {
        return leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
    }

    @Transactional
    public Report importLeagueSettings(LeagueId leagueId, LeagueSettings settings) {
        lock.acquire("history-import");
        var league = getLeague(leagueId);
        if (league.settings().equals(settings)) {
            return new Report(0, 1, 0, List.of());
        }
        leagues.updateSettings(leagueId, settings);
        return new Report(0, 0, 1, List.of());
    }

    @Transactional
    public Report importUsers(LeagueId leagueId, List<UserEntry> entries) {
        begin(leagueId);
        unique(entries, entry -> entry.user().id(), "user ID");
        unique(entries, UserEntry::identity, "Discord identity");
        var batch = new Batch();
        for (var entry : entries) {
            var user = entry.user();
            if (entry.identity().source() != UserIdentitySource.DISCORD) {
                throw new DomainValidationException("Only Discord identities can be imported");
            }
            var byId = users.findById(user.id());
            var byIdentity = users.findByIdentity(entry.identity());
            if (byIdentity.isPresent() && !byIdentity.get().id().equals(user.id())) {
                throw conflict("Discord identity already belongs to another user; expected " + user.id().value());
            }
            if (byId.isPresent() && (byIdentity.isEmpty() || !byId.get().equals(user))) {
                throw conflict("User already exists with different identity or creation time: " + user.id().value());
            }
            var participantId = new LeagueParticipantId(stableId("participant", leagueId.value(), user.id().value()));
            var participant = new LeagueParticipant(participantId, leagueId, user.id(), entry.displayName(), entry.joinedAt());
            var previous = participants.findByLeagueAndUser(leagueId, user.id());
            if (previous.isPresent()) {
                participant = new LeagueParticipant(previous.get().id(), leagueId, user.id(), entry.displayName(), entry.joinedAt());
                same(previous.get(), participant, "Participant for user " + user.id().value());
            } else if (participants.findById(participantId).isPresent()) {
                throw conflict("Participant ID already belongs to another user: " + participantId.value());
            }
            if (byId.isPresent() && previous.isPresent()) {
                batch.existing++;
            } else {
                var toCreate = participant;
                batch.create(() -> {
                    if (byId.isEmpty()) {
                        users.create(user, entry.identity());
                    }
                    if (previous.isEmpty()) {
                        participants.create(toCreate);
                    }
                });
            }
        }
        return batch.apply();
    }

    @Transactional
    public Report importRaces(LeagueId leagueId, List<Race> entries) {
        var league = begin(leagueId);
        unique(entries, Race::id, "race ID");
        unique(entries, race -> race.season() + ":" + race.round() + ":" + race.type(), "season/round/type");
        lockRaces(entries.stream().map(Race::id).toList());
        var calendar = new HashMap<Integer, List<Race>>();
        var batch = new Batch();
        for (var race : entries) {
            season(league, race.season());
            if (race.name().isBlank() || race.status() != RaceStatus.FINISHED && race.status() != RaceStatus.SCHEDULED) {
                throw new DomainValidationException("Import requires a race name and FINISHED or SCHEDULED status: " + race.id().value());
            }
            var sameKey = calendar.computeIfAbsent(race.season(), races::findAllBySeason).stream()
                    .filter(saved -> saved.round() == race.round() && saved.type() == race.type()).findFirst();
            if (sameKey.isPresent() && !sameKey.get().id().equals(race.id())) {
                throw conflict("Race key already uses another UUID: " + race.season() + "/" + race.round() + "/" + race.type());
            }
            batch.add(races.findById(race.id()), race, () -> races.create(race), "Race " + race.id().value());
        }
        return batch.apply();
    }

    @Transactional
    public Report importLineups(LeagueId leagueId, List<LineupEntry> entries) {
        var league = begin(leagueId);
        unique(entries, entry -> entry.lineup().raceId(), "lineup race ID");
        lockRaces(entries.stream().map(entry -> entry.lineup().raceId()).toList());
        var batch = new Batch();
        for (var entry : entries) {
            var lineup = entry.lineup();
            var race = race(league, lineup.raceId());
            var raceConditions = entry.conditions();
            if (raceConditions != null) {
                if (!race.id().equals(raceConditions.raceId()) || race.season() < 2026) {
                    throw new DomainValidationException("Unexpected prediction conditions for race " + race.id().value());
                }
                requireDriver(lineup, raceConditions.positionPredictionDriver());
            }
            batch.add(lineups.findByRaceId(race.id()), lineup, () -> {
                if (!lineups.createIfAbsent(lineup)) {
                    throw conflict("Lineup was concurrently created for race " + race.id().value());
                }
            }, "Lineup " + race.id().value());
            var previousConditions = conditions.findByRaceId(race.id());
            if (raceConditions == null) {
                if (previousConditions.isPresent()) {
                    throw conflict("Conditions already exist for race " + race.id().value());
                }
            } else {
                batch.add(previousConditions, raceConditions, () -> {
                    if (!conditions.createIfAbsent(raceConditions)) {
                        throw conflict("Conditions were concurrently created for race " + race.id().value());
                    }
                }, "Conditions " + race.id().value());
            }
        }
        return batch.apply();
    }

    @Transactional
    public Report importPredictions(LeagueId leagueId, List<PredictionEntry> entries) {
        var league = begin(leagueId);
        unique(entries, entry -> List.of(entry.raceId(), entry.userId()), "race/user prediction");
        lockRaces(entries.stream().map(PredictionEntry::raceId).toList());
        var batch = new Batch();
        for (var entry : entries) {
            var race = race(league, entry.raceId());
            requireFinished(race);
            var lineup = lineup(race.id());
            var participant = participant(leagueId, entry.userId());
            var previous = predictions.findByLeagueAndRaceAndParticipant(leagueId, race.id(), participant.id());
            var id = previous.map(Prediction::id).orElseGet(() ->
                    new PredictionId(stableId("prediction", race.id().value(), participant.id().value())));
            var prediction = new Prediction(id, leagueId, participant.id(), race.id(), entry.values(), entry.teamsPickedDrivers());
            validatePrediction(race, prediction, lineup);
            if (previous.isEmpty() && predictions.findById(id).isPresent()) {
                throw conflict("Prediction ID already belongs to another record: " + id.value());
            }
            if (previous.isEmpty() && results.findByRaceId(race.id()).isPresent()) {
                throw conflict("Import predictions before results for race " + race.id().value());
            }
            batch.add(previous, prediction, () -> predictions.create(prediction), "Prediction " + id.value());
        }
        return batch.apply();
    }

    @Transactional
    public Report importResults(LeagueId leagueId, List<ResultEntry> entries) {
        var league = begin(leagueId);
        unique(entries, entry -> entry.result().raceId(), "result race ID");
        lockRaces(entries.stream().map(entry -> entry.result().raceId()).toList());
        var batch = new Batch();
        for (var entry : entries) {
            var result = entry.result();
            var race = race(league, result.raceId());
            requireFinished(race);
            if (!result.rulesVersion().value().equals(String.valueOf(race.season())) || result.draft() != null) {
                throw new DomainValidationException("Historical result requires its season rules version and no draft: " + race.id().value());
            }
            var lineup = lineup(race.id());
            if (result.result() == null || result.result().classification() == null
                    || !drivers(lineup).equals(result.result().classification().stream()
                    .map(RaceClassificationEntry::driver).collect(Collectors.toSet()))) {
                throw new DomainValidationException("Result must classify every lineup driver with a status: " + race.id().value());
            }
            if (race.season() == 2023 && (result.result().safetyCars() != null || result.result().firstPlaceLead() != null)) {
                throw new DomainValidationException("Incidents and winner gap were not result categories in 2023");
            }
            var savedPredictions = predictions.findAllByRaceId(race.id()).stream()
                    .filter(prediction -> prediction.leagueId().equals(leagueId)).toList();
            var byParticipant = savedPredictions.stream().collect(Collectors.toMap(Prediction::participantId, Function.identity()));
            if (!entry.points().isEmpty()) {
                unique(entry.points(), PointsEntry::userId, "points user ID for race " + race.id().value());
            }
            if (entry.points().size() != savedPredictions.size()) {
                throw new DomainValidationException("Points must cover every imported prediction in this league: " + race.id().value());
            }
            var raceConditions = conditions.findByRaceId(race.id()).orElse(null);
            var calculated = PredictionPointsCalculator.calculate(race, result, raceConditions, lineup, savedPredictions).stream()
                    .collect(Collectors.toMap(PredictionPoints::predictionId, Function.identity()));
            batch.add(results.findByRaceId(race.id()), result, () -> results.create(result), "Result " + race.id().value());
            for (var value : entry.points()) {
                var participant = participant(leagueId, value.userId());
                var prediction = byParticipant.get(participant.id());
                if (prediction == null) {
                    throw new DomainValidationException("No prediction for user " + value.userId().value() + " in race " + race.id().value());
                }
                validateResultCategories(result, prediction);
                var imported = new PredictionPoints(prediction.id(), result.rulesVersion(), value.totalPoints(), value.tenthPlacePoints(),
                        value.dnfsPoints(), value.safetyCarsPoints(), value.firstPlaceLeadPoints(), value.driverPositionPoints(), value.teamMatesPoints());
                validatePoints(race, prediction, imported);
                if (!imported.equals(calculated.get(prediction.id()))) {
                    batch.differences.add(new PointsDifference(race.id(), value.userId(), imported, calculated.get(prediction.id())));
                }
                batch.add(points.findByPredictionId(prediction.id()), imported, () -> points.create(imported),
                        "Points for prediction " + prediction.id().value());
            }
        }
        return batch.apply();
    }

    @Transactional
    public Report importPodiums(LeagueId leagueId, List<PodiumEntry> entries) {
        var league = begin(leagueId);
        unique(entries, PodiumEntry::season, "podium season");
        var batch = new Batch();
        for (var entry : entries) {
            season(league, entry.season());
            unique(entry.userIds(), Function.identity(), "podium user ID");
            if (entry.userIds().size() > 3) {
                throw new DomainValidationException("A podium can contain at most three users");
            }
            var calendar = races.findAllBySeason(entry.season());
            lockRaces(calendar.stream().map(Race::id).toList());
            if (calendar.isEmpty()) {
                throw new DomainValidationException("No races for podium season " + entry.season());
            }
            for (var race : calendar) {
                requireFinished(race);
                if (results.findByRaceId(race.id()).filter(result -> result.result() != null).isEmpty()) {
                    throw new DomainValidationException("Import all season results before its podium: " + race.id().value());
                }
                for (var prediction : predictions.findAllByRaceId(race.id())) {
                    if (prediction.leagueId().equals(leagueId) && points.findByPredictionId(prediction.id()).isEmpty()) {
                        throw new DomainValidationException("Missing season points for prediction " + prediction.id().value());
                    }
                }
            }
            var participantIds = entry.userIds().stream().map(user -> participant(leagueId, user).id()).toList();
            var expected = standings.getStandings(leagueId, entry.season()).rows().stream()
                    .limit(3).map(row -> row.participantId()).toList();
            if (!participantIds.equals(expected)) {
                throw new DomainValidationException("Podium does not match saved standings and tie-break for season " + entry.season());
            }
            var podium = new SeasonPodiumEntry(leagueId, entry.season(), participantIds.getFirst(),
                    participantIds.size() > 1 ? participantIds.get(1) : null, participantIds.size() > 2 ? participantIds.get(2) : null);
            var previous = podiums.findAllByLeagueBeforeSeason(leagueId, entry.season() + 1).stream()
                    .filter(saved -> saved.season() == entry.season()).findFirst();
            batch.add(previous, podium, () -> podiums.save(podium), "Podium " + entry.season());
        }
        return batch.apply();
    }

    private League begin(LeagueId leagueId) {
        lock.acquire("history-import");
        return getLeague(leagueId);
    }

    private void lockRaces(List<RaceId> ids) {
        ids.stream().distinct().sorted(Comparator.comparing(RaceId::value))
                .forEach(id -> lock.acquire("race:" + id.value()));
    }

    private Race race(League league, RaceId raceId) {
        var race = races.findById(raceId).orElseThrow(() -> new RaceNotFoundException(raceId));
        season(league, race.season());
        return race;
    }

    private void season(League league, int season) {
        if (season < 2023 || season > 2026 || season < league.settings().firstSeason()) {
            throw new DomainValidationException("Import season must be 2023–2026 and not precede the league firstSeason: " + season);
        }
    }

    private RaceLineup lineup(RaceId raceId) {
        return lineups.findByRaceId(raceId).orElseThrow(() -> new RaceLineupNotFoundException(raceId));
    }

    private LeagueParticipant participant(LeagueId leagueId, UserId userId) {
        return participants.findByLeagueAndUser(leagueId, userId)
                .orElseThrow(() -> new DomainValidationException("Import the user and league participation first: " + userId.value()));
    }

    private void requireFinished(Race race) {
        if (race.status() != RaceStatus.FINISHED) {
            throw new DomainValidationException("Historical data requires a FINISHED race: " + race.id().value());
        }
    }

    private void validatePrediction(Race race, Prediction prediction, RaceLineup lineup) {
        var legacyTeams = prediction.teamsPickedDrivers();
        var forValidation = legacyTeams != null && legacyTeams.isEmpty()
                ? new Prediction(prediction.id(), prediction.leagueId(), prediction.participantId(), prediction.raceId(), prediction.values(), null)
                : prediction;
        PredictionValidator.validate(race.season(), forValidation);
        var values = prediction.values();
        requireDriver(lineup, values.tenthPlacePickedDriver());
        if (values.dnfPrediction() != null) {
            values.dnfPrediction().dnfPickedDrivers().forEach(driver -> requireDriver(lineup, driver));
        }
        if (legacyTeams != null) {
            legacyTeams.forEach(name -> requireDriver(lineup, new Driver(name)));
        }
        if (values.driverPositionPrediction() != null) {
            if (values.driverPositionPrediction() > drivers(lineup).size()) {
                throw new DomainValidationException("Predicted position exceeds the lineup size: " + race.id().value());
            }
            var saved = conditions.findByRaceId(race.id());
            if (saved.isEmpty() || saved.get().positionPredictionDriver() == null) {
                throw new DomainValidationException("Import the original position prediction driver first: " + race.id().value());
            }
        }
    }

    private void validateResultCategories(RaceResult result, Prediction prediction) {
        var values = prediction.values();
        if (values.safetyCarsPrediction() != null && result.result().safetyCars() == null
                || values.firstPlaceLeadPrediction() != null && result.result().firstPlaceLead() == null) {
            throw new DomainValidationException("Result is missing a category used by prediction " + prediction.id().value());
        }
    }

    private void validatePoints(Race race, Prediction prediction, PredictionPoints value) {
        var picks = prediction.values();
        category(value.tenthPlacePoints(), picks.tenthPlacePickedDriver() != null, 25, "tenthPlacePoints");
        category(value.dnfsPoints(), picks.dnfPrediction() != null, race.season() == 2023 ? 5 : 10, "dnfsPoints");
        category(value.safetyCarsPoints(), picks.safetyCarsPrediction() != null, 5, "safetyCarsPoints");
        category(value.firstPlaceLeadPoints(), picks.firstPlaceLeadPrediction() != null, 5, "firstPlaceLeadPoints");
        category(value.driverPositionPoints(), picks.driverPositionPrediction() != null, 10, "driverPositionPoints");
        category(value.teamMatesPoints(), (race.season() == 2024 || race.season() == 2025)
                && prediction.teamsPickedDrivers() != null, 10, "teamMatesPoints");
        var total = java.util.stream.Stream.of(value.tenthPlacePoints(), value.dnfsPoints(), value.safetyCarsPoints(),
                value.firstPlaceLeadPoints(), value.teamMatesPoints(), value.driverPositionPoints())
                .filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        if (value.totalPoints() != PredictionPointsRules.applySprintCoefficient(race.season(), race.type(), total)) {
            throw new DomainValidationException("Total does not match imported categories and sprint coefficient: " + prediction.id().value());
        }
    }

    private void category(Integer points, boolean present, int maximum, String name) {
        if ((points != null) != present || points != null && points > maximum) {
            throw new DomainValidationException("Invalid " + name + ": preserve null for absent categories and the original points otherwise");
        }
    }

    private Set<Driver> drivers(RaceLineup lineup) {
        return lineup.teams().stream().flatMap(team -> team.drivers().stream()).collect(Collectors.toSet());
    }

    private void requireDriver(RaceLineup lineup, Driver driver) {
        if (driver != null && !drivers(lineup).contains(driver)) {
            throw new DomainValidationException("Driver " + driver.name() + " does not belong to race " + lineup.raceId().value());
        }
    }

    private static UUID stableId(String kind, UUID first, UUID second) {
        return UUID.nameUUIDFromBytes(("f1-history:" + kind + ":" + first + ":" + second).getBytes(StandardCharsets.UTF_8));
    }

    private static <T, K> void unique(List<T> entries, Function<T, K> key, String description) {
        if (entries == null || entries.isEmpty() || entries.stream().anyMatch(Objects::isNull)) {
            throw new DomainValidationException("A non-empty list is required for " + description);
        }
        var keys = new HashSet<K>();
        for (var entry : entries) {
            if (!keys.add(key.apply(entry))) {
                throw new DomainValidationException("Duplicate " + description + ": " + key.apply(entry));
            }
        }
    }

    private static void same(Object previous, Object incoming, String description) {
        if (!Objects.equals(previous, incoming)) {
            throw conflict(description + " already exists with different data");
        }
    }

    private static ImportConflictException conflict(String detail) {
        return new ImportConflictException(detail);
    }

    private static final class Batch {
        private final List<Runnable> writes = new ArrayList<>();
        private final List<PointsDifference> differences = new ArrayList<>();
        private int existing;

        private void create(Runnable write) {
            writes.add(write);
        }

        private <T> void add(Optional<T> previous, T incoming, Runnable write, String description) {
            if (previous.isPresent()) {
                same(previous.get(), incoming, description);
                existing++;
            } else {
                create(write);
            }
        }

        private Report apply() {
            writes.forEach(Runnable::run);
            return new Report(writes.size(), existing, 0, differences);
        }
    }
}
