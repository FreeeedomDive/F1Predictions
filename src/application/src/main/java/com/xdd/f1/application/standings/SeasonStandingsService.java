package com.xdd.f1.application.standings;

import com.xdd.f1.application.league.LeagueAccessGuard;
import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.prediction.ParticipantRacePoints;
import com.xdd.f1.application.prediction.PredictionPointsRepository;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.result.RaceResultRepository;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.prediction.PredictionPointsRules;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeasonStandingsService {
    private static final Comparator<Race> EVENT_ORDER = Comparator.comparing(Race::closeAt)
            .thenComparingInt(Race::round)
            .thenComparingInt(race -> race.type() == RaceType.SPRINT ? 0 : 1)
            .thenComparing(race -> race.id().value());

    private final LeagueAccessGuard access;
    private final LeagueRepository leagues;
    private final RaceRepository races;
    private final PredictionPointsRepository points;
    private final RaceResultRepository results;

    public SeasonStandingsService(LeagueAccessGuard access, LeagueRepository leagues, RaceRepository races,
                                  PredictionPointsRepository points, RaceResultRepository results) {
        this.access = access;
        this.leagues = leagues;
        this.races = races;
        this.points = points;
        this.results = results;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SeasonStandings getStandings(UserId userId, LeagueId leagueId, int season) {
        access.checkAccess(userId, leagueId);
        var league = leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        var firstSeason = league.settings().firstSeason();
        if (season < firstSeason) {
            return SeasonStandings.empty(season);
        }

        var calendar = races.findAllBySeason(season);
        var publishedRaceIds = new HashSet<>(results.findPublishedRaceIdsBySeason(season));
        var history = new ArrayList<>(points.findAllByLeagueAndSeasons(leagueId, firstSeason, season));
        history.sort(Comparator.comparing(ParticipantRacePoints::race, EVENT_ORDER)
                .thenComparing(ParticipantRacePoints::participantJoinedAt)
                .thenComparing(saved -> saved.points().predictionId().value()));

        var currentSeason = history.stream().filter(saved -> saved.race().season() == season).toList();
        var visibleCalendar = calendar.stream()
                .filter(race -> league.settings().sprintsEnabled() || race.type() != RaceType.SPRINT)
                .toList();
        var events = completedEvents(visibleCalendar, currentSeason, publishedRaceIds);
        var potentialCalendar = potentialCalendar(visibleCalendar, currentSeason);
        var previousPodiums = previousPodiums(history, season);
        var rows = buildRows(currentSeason, previousPodiums);
        var charts = buildCharts(rows, events);
        var totalMaximum = maximumPoints(potentialCalendar);
        var pointsLeft = totalMaximum - maximumPoints(events);
        var leader = rows.stream().findFirst().orElse(null);

        return new SeasonStandings(
                season,
                events,
                rows,
                leader == null ? null : leader.participantId(),
                leader == null ? 0 : leader.totalPoints(),
                pointsLeft,
                charts,
                championChart(charts, events, totalMaximum)
        );
    }

    private List<Race> completedEvents(List<Race> calendar, List<ParticipantRacePoints> saved, Set<RaceId> publishedRaceIds) {
        var events = calendar.stream()
                .filter(race -> race.status() == RaceStatus.FINISHED && publishedRaceIds.contains(race.id()))
                .collect(Collectors.toMap(Race::id, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        saved.forEach(value -> events.putIfAbsent(value.race().id(), value.race()));
        return events.values().stream().sorted(EVENT_ORDER).toList();
    }

    private List<Race> potentialCalendar(List<Race> calendar, List<ParticipantRacePoints> saved) {
        var events = calendar.stream().collect(Collectors.toMap(
                Race::id, Function.identity(), (first, second) -> first, LinkedHashMap::new
        ));
        saved.forEach(value -> events.putIfAbsent(value.race().id(), value.race()));
        return events.values().stream().sorted(EVENT_ORDER).toList();
    }

    private Map<LeagueParticipantId, List<SeasonPodium>> previousPodiums(List<ParticipantRacePoints> history, int season) {
        var bySeason = history.stream()
                .filter(saved -> saved.race().season() < season)
                .collect(Collectors.groupingBy(saved -> saved.race().season(), TreeMap::new, Collectors.toList()));
        var podiums = new HashMap<LeagueParticipantId, List<SeasonPodium>>();
        bySeason.forEach((previousSeason, saved) -> {
            var rows = buildRows(saved, Map.of());
            for (var index = 0; index < Math.min(3, rows.size()); index++) {
                var row = rows.get(index);
                podiums.computeIfAbsent(row.participantId(), ignored -> new ArrayList<>())
                        .add(new SeasonPodium(previousSeason, index + 1));
            }
        });
        return podiums;
    }

    private List<SeasonStandingsRow> buildRows(List<ParticipantRacePoints> saved,
                                                Map<LeagueParticipantId, List<SeasonPodium>> previousPodiums) {
        var byParticipant = saved.stream().collect(Collectors.groupingBy(
                ParticipantRacePoints::participantId, LinkedHashMap::new, Collectors.toList()
        ));
        return byParticipant.values().stream().map(participantResults -> {
            var first = participantResults.getFirst();
            var results = participantResults.stream()
                    .map(value -> new SeasonRacePoints(value.race().id(), value.points()))
                    .toList();
            var totalPoints = participantResults.stream().mapToInt(value -> value.points().totalPoints()).sum();
            var maximumTenthPlaceCount = (int) participantResults.stream()
                    .filter(value -> Objects.equals(value.points().tenthPlacePoints(), PredictionPointsRules.MAX_TENTH_PLACE_POINTS))
                    .count();
            return new SeasonStandingsRow(
                    first.participantId(),
                    first.participantName(),
                    totalPoints,
                    maximumTenthPlaceCount,
                    results,
                    previousPodiums.getOrDefault(first.participantId(), List.of())
            );
        }).sorted(Comparator.comparingInt(SeasonStandingsRow::totalPoints).reversed()
                .thenComparing(Comparator.comparingInt(SeasonStandingsRow::maximumTenthPlaceCount).reversed())).toList();
    }

    private List<SeasonParticipantChart> buildCharts(List<SeasonStandingsRow> rows, List<Race> events) {
        return rows.stream().map(row -> {
            var byRace = row.results().stream().collect(Collectors.toMap(
                    SeasonRacePoints::raceId, result -> result.points().totalPoints()
            ));
            var chart = new ArrayList<Integer>();
            var total = 0;
            chart.add(0);
            for (var event : events) {
                total += byRace.getOrDefault(event.id(), 0);
                chart.add(total);
            }
            return new SeasonParticipantChart(row.participantId(), chart);
        }).toList();
    }

    private List<Integer> championChart(List<SeasonParticipantChart> charts, List<Race> events, int totalMaximum) {
        if (charts.isEmpty()) {
            return List.of();
        }
        var leaderPoints = charts.getFirst().points();
        var remaining = totalMaximum;
        var champion = new ArrayList<Integer>();
        champion.add(0);
        for (var index = 0; index < events.size(); index++) {
            remaining -= PredictionPointsRules.maxTotalPoints(events.get(index).season(), events.get(index).type());
            champion.add(Math.max(0, leaderPoints.get(index + 1) - remaining));
        }
        return champion;
    }

    private int maximumPoints(List<Race> events) {
        return events.stream().mapToInt(race -> PredictionPointsRules.maxTotalPoints(race.season(), race.type())).sum();
    }
}
