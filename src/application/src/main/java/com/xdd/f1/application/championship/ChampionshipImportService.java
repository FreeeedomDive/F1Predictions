package com.xdd.f1.application.championship;

import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.domain.championship.ChampionshipDriverStandings;
import com.xdd.f1.domain.championship.ChampionshipPrediction;
import com.xdd.f1.domain.championship.ChampionshipSeason;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueParticipantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xdd.f1.domain.championship.ChampionshipPredictionSource.USER;
import static com.xdd.f1.domain.championship.ChampionshipRulesVersion.V2026;
import static com.xdd.f1.domain.championship.ChampionshipStage.MID_SEASON;
import static com.xdd.f1.domain.championship.ChampionshipStage.PRE_SEASON;

@Service
public class ChampionshipImportService {
    private final ChampionshipSeasonRepository seasons;
    private final ChampionshipPredictionRepository predictions;
    private final LeagueRepository leagues;
    private final LeagueParticipantRepository participants;
    private final TransactionLock lock;
    private final Clock clock;

    public ChampionshipImportService(ChampionshipSeasonRepository seasons, ChampionshipPredictionRepository predictions,
                                      LeagueRepository leagues, LeagueParticipantRepository participants,
                                      TransactionLock lock, Clock clock) {
        this.seasons = seasons;
        this.predictions = predictions;
        this.leagues = leagues;
        this.participants = participants;
        this.lock = lock;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ChampionshipImportResult validate(LeagueId leagueId, ChampionshipSeason configuration,
                                             List<String> driverStandings, List<ChampionshipPrediction> imported) {
        return process(leagueId, configuration, driverStandings, imported, false);
    }

    @Transactional
    public ChampionshipImportResult load(LeagueId leagueId, ChampionshipSeason configuration,
                                         List<String> driverStandings, List<ChampionshipPrediction> imported) {
        lock.acquire("championship:" + configuration.season());
        return process(leagueId, configuration, driverStandings, imported, true);
    }

    private ChampionshipImportResult process(LeagueId leagueId, ChampionshipSeason configuration,
                                             List<String> driverStandings, List<ChampionshipPrediction> imported, boolean write) {
        var season = configuration.season();
        var league = leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        if (season < league.settings().firstSeason()) {
            throw new DomainValidationException("Championship season precedes the target league's first season");
        }
        if (configuration.rulesVersion() != V2026) {
            throw new DomainValidationException("Championship history must use " + V2026.value());
        }
        checkDrivers(configuration.preSeasonRoster(), "pre-season roster");
        checkDrivers(configuration.midSeasonRoster(), "mid-season roster");
        if (configuration.isOpen() && configuration.roster(configuration.stage()).isEmpty()) {
            throw new DomainValidationException("An open championship stage requires its roster");
        }
        var standings = driverStandings.isEmpty() ? null
                : new ChampionshipDriverStandings(season, driverStandings, null, clock.instant());

        var memberIds = participants.findAllByLeague(leagueId).stream().map(LeagueParticipant::id).collect(Collectors.toSet());
        var byStage = Map.of(
                PRE_SEASON, new LinkedHashMap<LeagueParticipantId, ChampionshipPrediction>(),
                MID_SEASON, new LinkedHashMap<LeagueParticipantId, ChampionshipPrediction>()
        );
        for (var prediction : imported) {
            var key = predictionKey(prediction);
            if (!prediction.leagueId().equals(leagueId) || prediction.season() != season || prediction.predictionSource() != USER) {
                throw new DomainValidationException("Invalid imported prediction: " + key);
            }
            if (!memberIds.contains(prediction.participantId())) {
                throw new DomainValidationException("Participant is not in the target league: " + key);
            }
            checkDrivers(prediction.orderedDrivers(), "prediction " + key);
            if (byStage.get(prediction.stage()).putIfAbsent(prediction.participantId(), prediction) != null) {
                throw new DomainValidationException("Duplicate prediction: " + key);
            }
        }

        var existingSeason = seasons.findBySeason(season);
        if (existingSeason.isPresent()) {
            if (!existingSeason.get().equals(configuration)) {
                throw new ChampionshipConflictException(season, "existing stage, rules version or rosters differ from the import");
            }
            var existingOrder = seasons.findDriverStandings(season)
                    .map(ChampionshipDriverStandings::orderedDrivers).orElseGet(List::of);
            if (!existingOrder.equals(driverStandings)) {
                throw new ChampionshipConflictException(season, "existing official standings differ from the import");
            }
        }
        var preSeasonCount = byStage.get(PRE_SEASON).size();
        var midSeasonCount = byStage.get(MID_SEASON).size();
        for (var existing : predictions.findAllByLeagueAndSeason(leagueId, season)) {
            var expected = byStage.get(existing.stage()).remove(existing.participantId());
            if (!existing.equals(expected)) {
                throw new ChampionshipConflictException(season, "existing prediction differs or is absent from the import: "
                        + predictionKey(existing));
            }
        }

        var newSeason = existingSeason.isEmpty();
        var newPredictions = byStage.get(PRE_SEASON).size() + byStage.get(MID_SEASON).size();
        if (write) {
            if (newSeason) {
                seasons.save(configuration);
                if (standings != null) {
                    seasons.saveDriverStandings(standings);
                }
            }
            byStage.values().forEach(stage -> stage.values().forEach(predictions::save));
        }
        return new ChampionshipImportResult(preSeasonCount, midSeasonCount, newSeason, newPredictions);
    }

    private static void checkDrivers(List<String> drivers, String field) {
        if (drivers.stream().anyMatch(String::isBlank)) {
            throw new DomainValidationException("Blank driver name in " + field);
        }
        if (new HashSet<>(drivers).size() != drivers.size()) {
            throw new DomainValidationException("Duplicate driver name in " + field);
        }
    }

    private static String predictionKey(ChampionshipPrediction prediction) {
        return prediction.participantId().value() + " / " + prediction.stage();
    }
}
