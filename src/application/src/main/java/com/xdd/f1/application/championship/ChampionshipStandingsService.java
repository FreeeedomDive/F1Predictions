package com.xdd.f1.application.championship;

import com.xdd.f1.application.championship.ChampionshipDetails.OwnPrediction;
import com.xdd.f1.application.championship.ChampionshipDetails.ParticipantPoints;
import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.domain.championship.ChampionshipDriverStandings;
import com.xdd.f1.domain.championship.ChampionshipPointsCalculator;
import com.xdd.f1.domain.championship.ChampionshipPrediction;
import com.xdd.f1.domain.championship.ChampionshipSeason;
import com.xdd.f1.domain.championship.ChampionshipStage;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static com.xdd.f1.domain.championship.ChampionshipStage.MID_SEASON;
import static com.xdd.f1.domain.championship.ChampionshipStage.PRE_SEASON;

@Service
public class ChampionshipStandingsService {
    private final ChampionshipSeasonRepository seasons;
    private final ChampionshipPredictionRepository predictions;
    private final LeagueRepository leagues;
    private final LeagueParticipantRepository participants;
    private final TransactionLock lock;
    private final Clock clock;

    public ChampionshipStandingsService(ChampionshipSeasonRepository seasons,
                                        ChampionshipPredictionRepository predictions, LeagueRepository leagues,
                                        LeagueParticipantRepository participants, TransactionLock lock, Clock clock) {
        this.seasons = seasons;
        this.predictions = predictions;
        this.leagues = leagues;
        this.participants = participants;
        this.lock = lock;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ChampionshipDetails getDetails(LeagueId leagueId, int season, LeagueParticipantId participantId) {
        participants.findByLeagueAndId(leagueId, participantId)
                .orElseThrow(() -> new LeagueParticipantNotFoundException(participantId));
        var league = leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        if (season < league.settings().firstSeason()) {
            return ChampionshipDetails.empty(season);
        }
        var configuration = seasons.findBySeason(season).orElse(null);
        if (configuration == null) {
            return ChampionshipDetails.empty(season);
        }

        var snapshot = seasons.findDriverStandings(season).orElse(null);
        var officialOrder = snapshot == null ? List.<String>of() : snapshot.orderedDrivers();
        var leaguePredictions = predictions.findAllByLeagueAndSeason(leagueId, season);
        return new ChampionshipDetails(season, configuration, snapshot,
                ownPrediction(configuration, PRE_SEASON, participantId, officialOrder, leaguePredictions),
                ownPrediction(configuration, MID_SEASON, participantId, officialOrder, leaguePredictions),
                publishedPoints(configuration, officialOrder, leaguePredictions));
    }

    @Transactional(readOnly = true)
    public SeasonState getSeasonState(int season) {
        return new SeasonState(seasons.findBySeason(season).orElse(null), seasons.findDriverStandings(season).orElse(null));
    }

    @Transactional
    public ChampionshipDriverStandings saveStandings(int season, List<String> orderedDrivers) {
        lock.acquire("championship:" + season);
        var existing = seasons.findDriverStandings(season).orElse(null);
        if (existing != null && existing.orderedDrivers().equals(orderedDrivers)) {
            return existing;
        }

        var updated = new ChampionshipDriverStandings(season, orderedDrivers,
                existing == null ? null : existing.lastSyncedRound(), clock.instant());
        seasons.saveDriverStandings(updated);
        return updated;
    }

    private OwnPrediction ownPrediction(ChampionshipSeason season, ChampionshipStage stage, LeagueParticipantId participantId,
                                        List<String> officialOrder, List<ChampionshipPrediction> leaguePredictions) {
        return leaguePredictions.stream()
                .filter(prediction -> prediction.participantId().equals(participantId) && prediction.stage() == stage)
                .findFirst()
                .map(prediction -> new OwnPrediction(prediction, points(season, officialOrder, prediction)))
                .orElse(null);
    }

    private List<ParticipantPoints> publishedPoints(ChampionshipSeason season, List<String> officialOrder,
                                                     List<ChampionshipPrediction> leaguePredictions) {
        if (officialOrder.isEmpty()) {
            return List.of();
        }
        var totals = new HashMap<LeagueParticipantId, Integer>();
        for (var prediction : leaguePredictions) {
            if (season.isPublished(prediction.stage())) {
                var stagePoints = points(season, officialOrder, prediction).stream().mapToInt(Integer::intValue).sum();
                totals.merge(prediction.participantId(), stagePoints, Integer::sum);
            }
        }
        var names = participants.findNamesByIds(List.copyOf(totals.keySet()));
        return totals.entrySet().stream()
                .map(entry -> new ParticipantPoints(entry.getKey(), names.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingInt(ParticipantPoints::totalPoints).reversed()
                        .thenComparing(row -> row.participantId().value()))
                .toList();
    }

    private List<Integer> points(ChampionshipSeason season, List<String> officialOrder, ChampionshipPrediction prediction) {
        return ChampionshipPointsCalculator.calculate(season, prediction.stage(), officialOrder, prediction.orderedDrivers());
    }

    public record SeasonState(ChampionshipSeason configuration, ChampionshipDriverStandings driverStandings) {
    }
}
