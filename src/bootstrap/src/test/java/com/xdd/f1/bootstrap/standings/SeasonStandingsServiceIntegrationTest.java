package com.xdd.f1.bootstrap.standings;

import com.xdd.f1.application.league.*;
import com.xdd.f1.application.prediction.PredictionPointsRepository;
import com.xdd.f1.application.prediction.PredictionRepository;
import com.xdd.f1.application.race.RaceRepository;
import com.xdd.f1.application.race.result.RaceResultRepository;
import com.xdd.f1.application.standings.SeasonPodium;
import com.xdd.f1.application.standings.SeasonStandings;
import com.xdd.f1.application.standings.SeasonStandingsRow;
import com.xdd.f1.application.standings.SeasonStandingsService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.league.*;
import com.xdd.f1.domain.prediction.*;
import com.xdd.f1.domain.race.*;
import com.xdd.f1.domain.race.result.RaceResult;
import com.xdd.f1.domain.race.result.RaceResultValues;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.infrastructure.persistence.race.RaceJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonStandingsServiceIntegrationTest extends AbstractIntegrationTest {
    private final SeasonStandingsService service;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final RaceRepository races;
    private final PredictionRepository predictions;
    private final PredictionPointsRepository points;
    private final RaceResultRepository results;
    private final RaceJpaRepository raceJpaRepository;
    private final TransactionTemplate transactions;

    private League league;
    private League otherLeague;
    private LeagueParticipant alice;
    private LeagueParticipant bob;
    private LeagueParticipant carol;
    private LeagueParticipant dave;
    private LeagueParticipant observer;
    private LeagueParticipant other;

    @Autowired
    SeasonStandingsServiceIntegrationTest(SeasonStandingsService service, LeagueRepository leagues,
                                          LeagueParticipantService participants, RaceRepository races,
                                          PredictionRepository predictions, PredictionPointsRepository points,
                                          RaceResultRepository results, RaceJpaRepository raceJpaRepository,
                                          PlatformTransactionManager transactionManager) {
        this.service = service;
        this.leagues = leagues;
        this.participants = participants;
        this.races = races;
        this.predictions = predictions;
        this.points = points;
        this.results = results;
        this.raceJpaRepository = raceJpaRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        league = createLeague(true, 2023);
        otherLeague = createLeague(true, 2023);
        alice = participant(league, "Alice");
        bob = participant(league, "Bob");
        carol = participant(league, "Carol");
        dave = participant(league, "Dave");
        observer = participant(league, "Observer");
        other = participant(otherLeague, "Other league name");
    }

    @AfterEach
    void cleanUp() {
        raceJpaRepository.deleteAll();
    }

    @Test
    void buildsLeagueScopedStandingsWithTieBreaksZeroResultsAndSharedChronology() {
        var first = race(2027, 1, RaceType.RACE, RaceStatus.FINISHED);
        var second = race(2027, 2, RaceType.RACE, RaceStatus.FINISHED);
        publish(first);
        publish(second);
        savePoints(first, alice, 0, 0);
        savePoints(first, bob, 25, 25);
        savePoints(first, carol, 0, 0);
        savePoints(second, alice, 25, 25);
        savePoints(second, bob, 0, 0);
        savePoints(second, carol, 25, 18);
        savePoints(first, other, 500, 25);

        var standings = get(league, alice, 2027);

        assertThat(standings.rows()).extracting(SeasonStandingsRow::participantName)
                .containsExactly("Alice", "Bob", "Carol");
        assertThat(standings.rows()).allMatch(row -> row.totalPoints() == 25);
        assertThat(standings.rows()).extracting(SeasonStandingsRow::maximumTenthPlaceCount)
                .containsExactly(1, 1, 0);
        assertThat(standings.rows()).noneMatch(row -> row.participantId().equals(observer.id()));
        assertThat(standings.rows().getFirst().results().getFirst().points().totalPoints()).isZero();
        assertThat(standings.events()).containsExactly(first, second);
        assertThat(standings.charts().get(0).points()).containsExactly(0, 0, 25);
        assertThat(standings.charts().get(1).points()).containsExactly(0, 25, 25);
        assertThat(standings.charts().get(2).points()).containsExactly(0, 0, 25);
        assertThat(standings.currentLeaderParticipantId()).isEqualTo(alice.id());
        assertThat(standings.currentLeaderPoints()).isEqualTo(25);
        assertThat(standings.pointsLeft()).isZero();
        assertThat(standings.championChart()).containsExactly(0, 0, 25);
        assertThatThrownBy(() -> get(league, other, 2027)).isInstanceOf(LeagueParticipantNotFoundException.class);
    }

    @Test
    void includesSavedHistoricalSprintsWhenCurrentLeagueSettingDisablesThem() {
        var historicalLeague = createLeague(false, 2023);
        var historicalAlice = participant(historicalLeague, "Historical Alice");
        var historicalBob = participant(historicalLeague, "Historical Bob");
        var sprint = race(2025, 1, RaceType.SPRINT, RaceStatus.FINISHED);
        var first = race(2025, 1, RaceType.RACE, RaceStatus.FINISHED);
        var second = race(2025, 2, RaceType.RACE, RaceStatus.FINISHED);
        var future = race(2025, 3, RaceType.RACE, RaceStatus.SCHEDULED);
        race(2025, 3, RaceType.SPRINT, RaceStatus.SCHEDULED);
        publish(sprint);
        publish(first);
        publish(second);
        savePoints(sprint, historicalAlice, 14, 25);
        savePoints(first, historicalAlice, 55, 25);
        savePoints(second, historicalAlice, 55, 25);
        savePoints(second, historicalBob, 5, 0);

        var standings = get(historicalLeague, historicalAlice, 2025);

        assertThat(standings.events()).containsExactly(sprint, first, second);
        assertThat(standings.rows()).extracting(SeasonStandingsRow::totalPoints).containsExactly(124, 5);
        assertThat(standings.charts().get(0).points()).containsExactly(0, 14, 69, 124);
        assertThat(standings.charts().get(1).points()).containsExactly(0, 0, 0, 5);
        assertThat(standings.pointsLeft()).isEqualTo(55);
        assertThat(standings.championChart()).containsExactly(0, 0, 0, 69);
        assertThat(standings.events()).doesNotContain(future);
    }

    @Test
    void keepsClosedAndUnpublishedFinishedRacesInRemainingMaximum() {
        var finished = race(2027, 1, RaceType.RACE, RaceStatus.FINISHED);
        var closed = race(2027, 2, RaceType.RACE, RaceStatus.CLOSED);
        var unpublished = race(2027, 3, RaceType.RACE, RaceStatus.FINISHED);
        var scheduled = race(2027, 4, RaceType.RACE, RaceStatus.SCHEDULED);
        var sprint = race(2027, 4, RaceType.SPRINT, RaceStatus.SCHEDULED);
        publish(finished);
        savePoints(finished, alice, 55, 25);

        var standings = get(league, alice, 2027);

        assertThat(standings.events()).containsExactly(finished);
        assertThat(standings.events()).doesNotContain(closed, unpublished, scheduled, sprint);
        assertThat(standings.pointsLeft()).isEqualTo(181);
        assertThat(standings.championChart()).containsExactly(0, 0);
    }

    @Test
    void buildsChampionChartFromFinalStandingsLeaderInsteadOfLeaderAtEachEvent() {
        var first = race(2027, 1, RaceType.RACE, RaceStatus.FINISHED);
        var second = race(2027, 2, RaceType.RACE, RaceStatus.FINISHED);
        var third = race(2027, 3, RaceType.RACE, RaceStatus.FINISHED);
        publish(first);
        publish(second);
        publish(third);
        savePoints(first, alice, 0, 0);
        savePoints(first, bob, 55, 25);
        savePoints(second, alice, 55, 25);
        savePoints(second, bob, 55, 0);
        savePoints(third, alice, 55, 25);
        savePoints(third, bob, 0, 0);

        var standings = get(league, alice, 2027);

        assertThat(standings.rows()).extracting(SeasonStandingsRow::participantName).containsExactly("Alice", "Bob");
        assertThat(standings.currentLeaderParticipantId()).isEqualTo(alice.id());
        assertThat(standings.charts().get(0).points()).containsExactly(0, 0, 55, 110);
        assertThat(standings.charts().get(1).points()).containsExactly(0, 55, 110, 110);
        assertThat(standings.championChart()).containsExactly(0, 0, 0, 110);
    }

    @Test
    void buildsPreviousPodiumsFromSelectedLeagueAndFirstSeason() {
        var historicalLeague = createLeague(true, 2024);
        var historicalAlice = participant(historicalLeague, "Alice");
        var historicalBob = participant(historicalLeague, "Bob");
        var historicalCarol = participant(historicalLeague, "Carol");
        var historicalDave = participant(historicalLeague, "Dave");
        var beforeFirstSeason = race(2023, 1, RaceType.RACE, RaceStatus.FINISHED);
        var previous = race(2024, 1, RaceType.RACE, RaceStatus.FINISHED);
        var current = race(2025, 1, RaceType.RACE, RaceStatus.FINISHED);
        savePoints(beforeFirstSeason, historicalDave, 100, 25);
        savePoints(previous, historicalAlice, 40, 25);
        savePoints(previous, historicalBob, 30, 18);
        savePoints(previous, historicalCarol, 20, 15);
        savePoints(previous, historicalDave, 10, 12);
        savePoints(previous, other, 1000, 25);
        savePoints(current, historicalAlice, 5, 0);
        savePoints(current, historicalBob, 4, 0);
        savePoints(current, historicalCarol, 3, 0);
        savePoints(current, historicalDave, 2, 0);

        var standings = get(historicalLeague, historicalAlice, 2025);

        assertThat(standings.rows()).hasSize(4);
        assertThat(standings.rows().get(0).previousPodiums()).containsExactly(new SeasonPodium(2024, 1));
        assertThat(standings.rows().get(1).previousPodiums()).containsExactly(new SeasonPodium(2024, 2));
        assertThat(standings.rows().get(2).previousPodiums()).containsExactly(new SeasonPodium(2024, 3));
        assertThat(standings.rows().get(3).previousPodiums()).isEmpty();
        assertThat(get(historicalLeague, historicalAlice, 2024).rows()).allMatch(row -> row.previousPodiums().isEmpty());
    }

    @Test
    void readsCorrectedSavedTotalsWithoutRecalculatingLegacyRules() {
        var race = race(2027, 1, RaceType.RACE, RaceStatus.FINISHED);
        publish(race);
        var alicePoints = savePoints(race, alice, 10, 0);
        savePoints(race, bob, 20, 18);
        assertThat(get(league, alice, 2027).currentLeaderParticipantId()).isEqualTo(bob.id());
        var corrected = new PredictionPoints(alicePoints.predictionId(), new RulesVersion("unknown-legacy"),
                30, 25, null, null, null, null, null);
        transactions.executeWithoutResult(status -> points.update(corrected));

        var standings = get(league, alice, 2027);

        assertThat(standings.currentLeaderParticipantId()).isEqualTo(alice.id());
        assertThat(standings.rows()).extracting(SeasonStandingsRow::totalPoints).containsExactly(30, 20);
        assertThat(standings.charts().getFirst().points()).containsExactly(0, 30);
        assertThat(standings.championChart()).containsExactly(0, 30);
    }

    @Test
    void returnsEmptyStateBeforeFirstSeasonAndWhenSeasonHasNoScores() {
        var futureLeague = createLeague(true, 2024);
        var participant = participant(futureLeague, "Future participant");
        race(2027, 1, RaceType.RACE, RaceStatus.SCHEDULED);

        var beforeFirstSeason = get(futureLeague, participant, 2023);
        var withoutScores = get(futureLeague, participant, 2027);

        assertThat(beforeFirstSeason).isEqualTo(SeasonStandings.empty(2023));
        assertThat(withoutScores.rows()).isEmpty();
        assertThat(withoutScores.events()).isEmpty();
        assertThat(withoutScores.charts()).isEmpty();
        assertThat(withoutScores.championChart()).isEmpty();
        assertThat(withoutScores.currentLeaderParticipantId()).isNull();
        assertThat(withoutScores.currentLeaderPoints()).isZero();
        assertThat(withoutScores.pointsLeft()).isEqualTo(55);
    }

    private League createLeague(boolean sprintsEnabled, int firstSeason) {
        var created = new League(new LeagueId(UUID.randomUUID()), "Standings league", new LeagueSource.Manual(),
                new LeagueSettings(null, sprintsEnabled, firstSeason), Instant.now());
        transactions.executeWithoutResult(status -> leagues.create(created));
        return created;
    }

    private LeagueParticipant participant(League league, String name) {
        return participants.ensureParticipant(league.id(),
                new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString()), name);
    }

    private Race race(int season, int round, RaceType type, RaceStatus status) {
        var closeAt = Instant.parse(String.format("%04d-03-01T00:00:00Z", season))
                .plusSeconds(round * 86400L + (type == RaceType.RACE ? 3600 : 0));
        var race = new Race(new RaceId(UUID.randomUUID()), season, round, type,
                season + " " + type + " " + round, status, closeAt.minusSeconds(3600), closeAt);
        transactions.executeWithoutResult(state -> races.create(race));
        return race;
    }

    private void publish(Race race) {
        transactions.executeWithoutResult(status -> results.create(new RaceResult(
                race.id(), new RulesVersion("legacy-" + race.season()), new RaceResultValues(null, null, null), null
        )));
    }

    private PredictionPoints savePoints(Race race, LeagueParticipant participant, int total, Integer tenth) {
        var prediction = new Prediction(new PredictionId(UUID.randomUUID()), participant.leagueId(), participant.id(), race.id(),
                new PredictionValues(null, null, null, null, null), null);
        var saved = new PredictionPoints(prediction.id(), new RulesVersion("unknown-legacy"), total,
                tenth, null, null, null, null, null);
        transactions.executeWithoutResult(status -> {
            predictions.create(prediction);
            points.create(saved);
        });
        return saved;
    }

    private SeasonStandings get(League league, LeagueParticipant participant, int season) {
        return service.getStandings(participant.userId(), league.id(), season);
    }
}
