package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipConflictException;
import com.xdd.f1.application.championship.ChampionshipPredictionRepository;
import com.xdd.f1.application.championship.ChampionshipPredictionService;
import com.xdd.f1.application.championship.ChampionshipPredictionsClosedException;
import com.xdd.f1.application.championship.ChampionshipSeasonNotFoundException;
import com.xdd.f1.application.championship.ChampionshipSeasonRepository;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.championship.ChampionshipPrediction;
import com.xdd.f1.domain.championship.ChampionshipStage;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.lineup.Team;
import com.xdd.f1.domain.race.lineup.TeamBranding;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.xdd.f1.domain.championship.ChampionshipRulesVersion.V2026;
import static com.xdd.f1.domain.championship.ChampionshipRulesVersion.V2027;
import static com.xdd.f1.domain.championship.ChampionshipStage.MID_SEASON;
import static com.xdd.f1.domain.championship.ChampionshipStage.PRE_SEASON;
import static com.xdd.f1.domain.championship.ChampionshipPredictionSource.PRE_SEASON_COPY;
import static com.xdd.f1.domain.championship.ChampionshipPredictionSource.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChampionshipPredictionServiceIntegrationTest extends AbstractIntegrationTest {
    private static final int SEASON = 2027;
    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");
    private static final List<String> PRE_ROSTER = List.of("Verstappen", "Norris");
    private static final List<String> MID_ROSTER = List.of("Piastri", "Norris");

    private final ChampionshipPredictionService service;
    private final ChampionshipSeasonRepository seasons;
    private final ChampionshipPredictionRepository predictions;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final RaceService races;
    private final RaceLineupRepository lineups;
    private final TransactionTemplate transactions;
    private final JdbcClient jdbc;

    @Autowired
    ChampionshipPredictionServiceIntegrationTest(
            ChampionshipPredictionService service, ChampionshipSeasonRepository seasons,
            ChampionshipPredictionRepository predictions, LeagueRepository leagues,
            LeagueParticipantService participants, RaceService races, RaceLineupRepository lineups,
            PlatformTransactionManager transactionManager, JdbcClient jdbc
    ) {
        this.service = service;
        this.seasons = seasons;
        this.predictions = predictions;
        this.leagues = leagues;
        this.participants = participants;
        this.races = races;
        this.lineups = lineups;
        this.transactions = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM championship_seasons WHERE season = :season").param("season", SEASON).update();
        jdbc.sql("DELETE FROM races WHERE season = :season AND name = 'Championship test race'").param("season", SEASON).update();
    }

    @Test
    void switchesStagesInEitherDirectionAndKeepsTheirFrozenRostersAndRules() {
        var sprint = createLineup(1, RaceType.SPRINT, PRE_ROSTER);
        var preRace = createLineup(1, RaceType.RACE, PRE_ROSTER);
        var midRace = createLineup(2, RaceType.RACE, MID_ROSTER);
        assertThat(seasons.findBySeason(SEASON)).isEmpty();
        var configured = service.configure(SEASON, V2027);
        assertThat(service.configure(SEASON, V2027)).isEqualTo(configured);
        assertThatThrownBy(() -> service.configure(SEASON, V2026)).isInstanceOf(ChampionshipConflictException.class);
        assertThatThrownBy(() -> service.openStage(SEASON, PRE_SEASON, null)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.openStage(SEASON, PRE_SEASON, sprint)).isInstanceOf(DomainValidationException.class);

        var opened = service.openStage(SEASON, PRE_SEASON, preRace);
        transactions.executeWithoutResult(status -> lineups.update(lineup(preRace, MID_ROSTER)));
        assertThat(seasons.findBySeason(SEASON)).contains(opened);
        var mid = service.openStage(SEASON, MID_SEASON, midRace);
        assertThat(mid.preSeasonRoster()).containsExactlyElementsOf(PRE_ROSTER);
        assertThat(mid.midSeasonRoster()).containsExactlyElementsOf(MID_ROSTER);
        assertThat(mid.rulesVersion()).isEqualTo(V2027);
        assertThat(service.openStage(SEASON, MID_SEASON, null)).isEqualTo(mid);
        var preClosed = service.closeStage(SEASON, PRE_SEASON);
        assertThat(preClosed.stage()).isEqualTo(PRE_SEASON);
        assertThat(preClosed.isOpen()).isFalse();
        assertThat(preClosed.isPublished(MID_SEASON)).isFalse();
        assertThat(service.openStage(SEASON, PRE_SEASON, null).preSeasonRoster()).containsExactlyElementsOf(PRE_ROSTER);
        assertThat(service.openStage(SEASON, MID_SEASON, null)).isEqualTo(mid);
    }

    @Test
    void savesOnlyTheOpenStageForTheLeagueParticipantAndUpdatesItsOrder() {
        var identity = identity();
        var member = createParticipant(identity, SEASON);
        var other = createParticipant(identity, SEASON);
        var future = createParticipant(identity, SEASON + 1);
        var race = createLineup(1, RaceType.RACE, PRE_ROSTER);
        service.configure(SEASON, V2027);
        assertThatThrownBy(() -> save(member, PRE_SEASON, PRE_ROSTER)).isInstanceOf(ChampionshipPredictionsClosedException.class);
        service.openStage(SEASON, PRE_SEASON, race);

        for (var invalid : List.of(List.<String>of(), List.of("Norris"), List.of("Norris", "Norris"), MID_ROSTER)) {
            assertThatThrownBy(() -> save(member, PRE_SEASON, invalid)).isInstanceOf(DomainValidationException.class);
        }
        assertThatThrownBy(() -> service.save(other.leagueId(), SEASON, member.id(), PRE_SEASON, PRE_ROSTER))
                .isInstanceOf(LeagueParticipantNotFoundException.class);
        assertThatThrownBy(() -> save(future, PRE_SEASON, PRE_ROSTER)).isInstanceOf(ChampionshipSeasonNotFoundException.class);
        assertThatThrownBy(() -> save(member, MID_SEASON, PRE_ROSTER)).isInstanceOf(ChampionshipPredictionsClosedException.class);
        assertThat(predictions.findAllBySeason(SEASON)).isEmpty();

        save(member, PRE_SEASON, PRE_ROSTER);
        var updated = save(member, PRE_SEASON, PRE_ROSTER.reversed());
        var foreign = save(other, PRE_SEASON, PRE_ROSTER);
        assertThat(find(member, PRE_SEASON)).contains(updated);
        assertThat(find(member, MID_SEASON)).isEmpty();
        assertThat(find(other, PRE_SEASON)).contains(foreign);
        assertThat(predictions.findByLeagueAndSeasonAndParticipantAndStage(other.leagueId(), SEASON, member.id(), PRE_SEASON))
                .isEmpty();
        assertThat(predictions.findAllBySeason(SEASON)).containsExactlyInAnyOrder(updated, foreign);
        service.closeStage(SEASON, PRE_SEASON);
        assertThatThrownBy(() -> save(member, PRE_SEASON, PRE_ROSTER)).isInstanceOf(ChampionshipPredictionsClosedException.class);
    }

    @Test
    void copiesOnlyMissingMidSeasonPredictionsAndDoesNotReorderThem() {
        var identity = identity();
        var withoutMidSeason = createParticipant(identity, SEASON);
        var explicit = createParticipant(identity, SEASON);
        var onlyMidSeason = participants.ensureParticipant(explicit.leagueId(), identity(), "Only mid-season");
        var neither = participants.ensureParticipant(explicit.leagueId(), identity(), "Neither");
        var future = createParticipant(identity, SEASON + 1);
        var preRace = createLineup(1, RaceType.RACE, PRE_ROSTER);
        var midRace = createLineup(2, RaceType.RACE, MID_ROSTER);
        service.configure(SEASON, V2027);
        service.openStage(SEASON, PRE_SEASON, preRace);
        var pre = save(withoutMidSeason, PRE_SEASON, PRE_ROSTER.reversed());
        save(explicit, PRE_SEASON, PRE_ROSTER);
        seed(future, PRE_SEASON, PRE_ROSTER);
        service.closeStage(SEASON, PRE_SEASON);
        service.openStage(SEASON, MID_SEASON, midRace);

        var second = save(explicit, MID_SEASON, MID_ROSTER);
        save(onlyMidSeason, MID_SEASON, MID_ROSTER);
        assertThat(find(onlyMidSeason, PRE_SEASON)).isEmpty();
        assertThat(find(withoutMidSeason, MID_SEASON)).isEmpty();

        var closed = service.closeStage(SEASON, MID_SEASON);
        assertThat(closed.isOpen()).isFalse();
        assertThat(service.closeStage(SEASON, MID_SEASON)).isEqualTo(closed);
        assertThat(find(withoutMidSeason, MID_SEASON)).contains(new ChampionshipPrediction(
                withoutMidSeason.leagueId(), SEASON, withoutMidSeason.id(), MID_SEASON, pre.orderedDrivers(), PRE_SEASON_COPY));
        assertThat(find(explicit, MID_SEASON)).contains(second);
        assertThat(find(neither, MID_SEASON)).isEmpty();
        assertThat(find(future, MID_SEASON)).isEmpty();
    }

    @Test
    void keepsImportedAndIdenticalSecondPredictionsAsUser() {
        var member = createParticipant(identity(), SEASON);
        var other = participants.ensureParticipant(member.leagueId(), identity(), "Other");
        var race = createLineup(1, RaceType.RACE, PRE_ROSTER);
        service.configure(SEASON, V2027);
        service.openStage(SEASON, PRE_SEASON, race);
        save(member, PRE_SEASON, PRE_ROSTER);
        save(other, PRE_SEASON, PRE_ROSTER);
        service.closeStage(SEASON, PRE_SEASON);
        service.openStage(SEASON, MID_SEASON, race);
        var imported = seed(member, MID_SEASON, PRE_ROSTER.reversed());
        var identical = save(other, MID_SEASON, PRE_ROSTER);

        assertThat(service.closeStage(SEASON, MID_SEASON).isOpen()).isFalse();
        assertThat(identical.predictionSource()).isEqualTo(USER);
        assertThat(find(member, MID_SEASON)).contains(imported);
        assertThat(find(other, MID_SEASON)).contains(identical);
    }

    @Test
    void rollingBackMidSeasonPublicationRemovesOnlyCopiesAndAllowsNewPredictions() {
        var member = createParticipant(identity(), SEASON);
        var other = participants.ensureParticipant(member.leagueId(), identity(), "Other");
        var preRace = createLineup(1, RaceType.RACE, PRE_ROSTER);
        var midRace = createLineup(2, RaceType.RACE, MID_ROSTER);
        service.configure(SEASON, V2027);
        service.openStage(SEASON, PRE_SEASON, preRace);
        save(member, PRE_SEASON, PRE_ROSTER);
        service.openStage(SEASON, MID_SEASON, midRace);
        var ownMid = save(other, MID_SEASON, MID_ROSTER.reversed());
        service.closeStage(SEASON, MID_SEASON);
        assertThat(find(member, MID_SEASON)).get().extracting(ChampionshipPrediction::predictionSource)
                .isEqualTo(PRE_SEASON_COPY);

        service.openStage(SEASON, PRE_SEASON, null);
        assertThat(find(member, MID_SEASON)).isEmpty();
        assertThat(find(other, MID_SEASON)).contains(ownMid);
        var updatedPre = save(member, PRE_SEASON, PRE_ROSTER.reversed());
        service.closeStage(SEASON, MID_SEASON);
        assertThat(find(member, MID_SEASON)).get().extracting(ChampionshipPrediction::orderedDrivers)
                .isEqualTo(updatedPre.orderedDrivers());

        service.openStage(SEASON, MID_SEASON, null);
        assertThat(find(member, MID_SEASON)).isEmpty();
        var ownUpdatedMid = save(member, MID_SEASON, MID_ROSTER);
        service.closeStage(SEASON, MID_SEASON);
        assertThat(find(member, MID_SEASON)).contains(ownUpdatedMid);
        assertThat(find(other, MID_SEASON)).contains(ownMid);
    }

    @Test
    void doesNotLetASaveRacingWithCloseReplaceTheCopy() throws Exception {
        var member = createParticipant(identity(), SEASON);
        var race = createLineup(1, RaceType.RACE, PRE_ROSTER);
        service.configure(SEASON, V2027);
        service.openStage(SEASON, PRE_SEASON, race);
        save(member, PRE_SEASON, PRE_ROSTER);
        service.closeStage(SEASON, PRE_SEASON);
        service.openStage(SEASON, MID_SEASON, race);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var started = new CountDownLatch(1);
            var late = transactions.execute(status -> {
                service.closeStage(SEASON, MID_SEASON);
                var save = executor.submit(() -> {
                    started.countDown();
                    return save(member, MID_SEASON, PRE_ROSTER.reversed());
                });
                await(started);
                assertThat(save.isDone()).isFalse();
                return save;
            });
            assertThatThrownBy(() -> late.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ChampionshipPredictionsClosedException.class);
        }
        var copy = find(member, MID_SEASON).orElseThrow();
        assertThat(copy.orderedDrivers()).containsExactlyElementsOf(PRE_ROSTER);
        assertThat(copy.predictionSource()).isEqualTo(PRE_SEASON_COPY);
    }

    @Test
    void repositoryRejectsAnAssociationToAParticipantInAnotherLeague() {
        var member = createParticipant(identity(), SEASON);
        var other = createParticipant(identity(), SEASON);
        service.configure(SEASON, V2027);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> predictions.save(new ChampionshipPrediction(
                other.leagueId(), SEASON, member.id(), PRE_SEASON, PRE_ROSTER, USER))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_championship_predictions_league_participant");
    }

    private ChampionshipPrediction save(LeagueParticipant participant, ChampionshipStage stage, List<String> drivers) {
        return service.save(participant.leagueId(), SEASON, participant.id(), stage, drivers);
    }

    private Optional<ChampionshipPrediction> find(LeagueParticipant participant, ChampionshipStage stage) {
        return predictions.findByLeagueAndSeasonAndParticipantAndStage(participant.leagueId(), SEASON, participant.id(), stage);
    }

    private ChampionshipPrediction seed(LeagueParticipant participant, ChampionshipStage stage, List<String> drivers) {
        var prediction = new ChampionshipPrediction(participant.leagueId(), SEASON, participant.id(), stage, drivers, USER);
        transactions.executeWithoutResult(status -> predictions.save(prediction));
        return prediction;
    }

    private LeagueParticipant createParticipant(UserIdentityId identity, int firstSeason) {
        var league = new League(new LeagueId(UUID.randomUUID()), "Championship league", new LeagueSource.Manual(),
                new LeagueSettings(null, false, firstSeason), NOW);
        transactions.executeWithoutResult(status -> leagues.create(league));
        return participants.ensureParticipant(league.id(), identity, "Participant");
    }

    private UserIdentityId identity() {
        return new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
    }

    private RaceId createLineup(int round, RaceType type, List<String> names) {
        var raceId = races.create(SEASON, round, type, "Championship test race", NOW, NOW.plusSeconds(3600)).id();
        transactions.executeWithoutResult(status -> lineups.createIfAbsent(lineup(raceId, names)));
        return raceId;
    }

    private RaceLineup lineup(RaceId raceId, List<String> names) {
        var team = new Team(new TeamBranding("Team", "TEM", "#112233", "#FFFFFF"), names.stream().map(Driver::new).toList());
        return new RaceLineup(raceId, List.of(team));
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
