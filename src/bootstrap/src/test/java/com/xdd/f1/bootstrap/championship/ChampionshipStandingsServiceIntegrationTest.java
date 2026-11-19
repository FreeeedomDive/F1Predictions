package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipDetails;
import com.xdd.f1.application.championship.ChampionshipDetails.ParticipantPoints;
import com.xdd.f1.application.championship.ChampionshipPredictionRepository;
import com.xdd.f1.application.championship.ChampionshipPredictionService;
import com.xdd.f1.application.championship.ChampionshipSeasonNotFoundException;
import com.xdd.f1.application.championship.ChampionshipSeasonRepository;
import com.xdd.f1.application.championship.ChampionshipStandingsService;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.championship.ChampionshipDriverStandings;
import com.xdd.f1.domain.championship.ChampionshipPrediction;
import com.xdd.f1.domain.championship.ChampionshipSeason;
import com.xdd.f1.domain.championship.ChampionshipStage;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.xdd.f1.domain.championship.ChampionshipRulesVersion.V2027;
import static com.xdd.f1.domain.championship.ChampionshipStage.MID_SEASON;
import static com.xdd.f1.domain.championship.ChampionshipStage.PRE_SEASON;
import static com.xdd.f1.domain.championship.ChampionshipSubmissionSource.PRE_SEASON_COPY;
import static com.xdd.f1.domain.championship.ChampionshipSubmissionSource.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChampionshipStandingsServiceIntegrationTest extends AbstractIntegrationTest {
    private static final int SEASON = 2028;
    private static final Instant EARLIER = Instant.parse("2028-07-01T12:00:00Z");
    private static final List<String> PRE_ROSTER = List.of("Verstappen", "Norris");
    private static final List<String> MID_ROSTER = List.of("Piastri", "Norris");
    private static final List<String> OFFICIAL_ORDER = List.of("Piastri", "Norris", "Verstappen", "Hamilton");

    private final ChampionshipStandingsService service;
    private final ChampionshipPredictionService predictionService;
    private final ChampionshipPredictionRepository predictions;
    private final ChampionshipSeasonRepository seasons;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final TransactionTemplate transactions;
    private final JdbcClient jdbc;

    @Autowired
    ChampionshipStandingsServiceIntegrationTest(
            ChampionshipStandingsService service, ChampionshipPredictionService predictionService,
            ChampionshipPredictionRepository predictions,
            ChampionshipSeasonRepository seasons, LeagueRepository leagues, LeagueParticipantService participants,
            PlatformTransactionManager transactionManager, JdbcClient jdbc
    ) {
        this.service = service;
        this.predictionService = predictionService;
        this.predictions = predictions;
        this.seasons = seasons;
        this.leagues = leagues;
        this.participants = participants;
        this.transactions = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM championship_seasons WHERE season = :season").param("season", SEASON).update();
    }

    @Test
    void readsEmptyStatesWithoutPublishingPointsAndRequiresMembership() {
        var member = createParticipant(createLeague(SEASON), "Member");
        var other = createParticipant(createLeague(SEASON), "Other");
        var future = createParticipant(createLeague(SEASON + 1), "Future");
        assertThat(details(member)).isEqualTo(ChampionshipDetails.empty(SEASON));
        assertThatThrownBy(() -> service.getDetails(member.leagueId(), SEASON, other.id()))
                .isInstanceOf(LeagueParticipantNotFoundException.class);

        var configuration = new ChampionshipSeason(SEASON, V2027, PRE_SEASON, true, false, PRE_ROSTER, List.of());
        transactions.executeWithoutResult(status -> seasons.save(configuration));
        var own = seed(member, PRE_SEASON, PRE_ROSTER);
        var pending = details(member);
        assertThat(pending.configuration()).isEqualTo(configuration);
        assertThat(pending.driverStandings()).isNull();
        assertThat(pending.preSeasonPrediction().prediction()).isEqualTo(own);
        assertThat(pending.preSeasonPrediction().points()).isEmpty();
        assertThat(pending.midSeasonPrediction()).isNull();
        assertThat(pending.rows()).isEmpty();
        assertThat(details(future)).isEqualTo(ChampionshipDetails.empty(SEASON));
    }

    @Test
    void savesTheOfficialOrderWithoutChangingTheStageOrLosingTheLastSyncedRound() {
        assertThatThrownBy(() -> service.saveStandings(SEASON, OFFICIAL_ORDER))
                .isInstanceOf(ChampionshipSeasonNotFoundException.class);
        var configuration = predictionService.configure(SEASON, V2027);
        assertThatThrownBy(() -> service.saveStandings(SEASON, List.of())).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.saveStandings(SEASON, List.of("Norris", " "))).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.saveStandings(SEASON, List.of("Norris", "Norris")))
                .isInstanceOf(DomainValidationException.class);
        assertThat(seasons.findDriverStandings(SEASON)).isEmpty();

        assertThat(service.saveStandings(SEASON, OFFICIAL_ORDER).lastSyncedRound()).isNull();
        var synced = new ChampionshipDriverStandings(SEASON, OFFICIAL_ORDER, 10, EARLIER);
        transactions.executeWithoutResult(status -> seasons.saveDriverStandings(synced));
        assertThat(service.saveStandings(SEASON, OFFICIAL_ORDER)).isEqualTo(synced);

        var correctedOrder = OFFICIAL_ORDER.reversed();
        service.saveStandings(SEASON, correctedOrder);
        var corrected = seasons.findDriverStandings(SEASON).orElseThrow();
        assertThat(corrected.orderedDrivers()).containsExactlyElementsOf(correctedOrder);
        assertThat(corrected.lastSyncedRound()).isEqualTo(10);
        assertThat(corrected.updatedAt()).isNotEqualTo(EARLIER);
        assertThat(seasons.findBySeason(SEASON)).contains(configuration);
    }

    @Test
    void exposesOnlyOwnRankingsAndCalculatesPublishedLeagueTotalsFromTheLatestSnapshot() {
        var league = createLeague(SEASON);
        var alice = createParticipant(league, "Alice");
        var bob = createParticipant(league, "Bob");
        var carol = createParticipant(league, "Carol");
        var onlyMid = createParticipant(league, "Mid-season only");
        var foreign = createParticipant(createLeague(SEASON), "Foreign league");
        var configuration = new ChampionshipSeason(SEASON, V2027, MID_SEASON, true, true, PRE_ROSTER, MID_ROSTER);
        transactions.executeWithoutResult(status -> seasons.save(configuration));
        service.saveStandings(SEASON, OFFICIAL_ORDER);
        var pre = seed(alice, PRE_SEASON, PRE_ROSTER.reversed());
        var mid = seed(alice, MID_SEASON, MID_ROSTER.reversed());
        seed(bob, PRE_SEASON, PRE_ROSTER);
        seed(carol, PRE_SEASON, PRE_ROSTER);
        seed(onlyMid, MID_SEASON, MID_ROSTER);
        seed(foreign, PRE_SEASON, PRE_ROSTER.reversed());

        var whileOpen = details(alice);
        assertThat(whileOpen.driverStandings().orderedDrivers()).containsExactlyElementsOf(OFFICIAL_ORDER);
        assertThat(whileOpen.preSeasonPrediction().prediction()).isEqualTo(pre);
        assertThat(whileOpen.preSeasonPrediction().points()).containsExactly(20, 20);
        assertThat(whileOpen.midSeasonPrediction().prediction()).isEqualTo(mid);
        assertThat(whileOpen.midSeasonPrediction().points()).isEmpty();
        var tiedRows = Stream.of(bob, carol).sorted(Comparator.comparing(member -> member.id().value()))
                .map(member -> new ParticipantPoints(member.id(), member.displayName(), 28)).toList();
        assertThat(whileOpen.rows()).containsExactly(new ParticipantPoints(alice.id(), "Alice", 40),
                tiedRows.getFirst(), tiedRows.getLast());
        assertThat(details(bob).midSeasonPrediction()).isNull();

        predictionService.closeStage(SEASON, MID_SEASON);
        var closed = details(alice);
        assertThat(closed.driverStandings()).isEqualTo(whileOpen.driverStandings());
        assertThat(closed.midSeasonPrediction().points()).containsExactly(6, 6);
        assertThat(closed.rows().getFirst()).isEqualTo(new ParticipantPoints(alice.id(), "Alice", 52));
        assertThat(closed.rows()).contains(new ParticipantPoints(onlyMid.id(), onlyMid.displayName(), 20));
        var copy = details(bob).midSeasonPrediction();
        assertThat(copy.prediction().submissionSource()).isEqualTo(PRE_SEASON_COPY);
        assertThat(copy.points()).containsExactly(0, 10);

        service.saveStandings(SEASON, List.of("Verstappen", "Norris", "Piastri", "Hamilton"));
        assertThat(details(alice).rows().getFirst()).isEqualTo(new ParticipantPoints(alice.id(), "Alice", 48));
    }

    private ChampionshipDetails details(LeagueParticipant member) {
        return service.getDetails(member.leagueId(), SEASON, member.id());
    }

    private ChampionshipPrediction seed(LeagueParticipant member, ChampionshipStage stage, List<String> drivers) {
        var prediction = new ChampionshipPrediction(member.leagueId(), SEASON, member.id(), stage, drivers, USER);
        transactions.executeWithoutResult(status -> predictions.save(prediction));
        return prediction;
    }

    private League createLeague(int firstSeason) {
        var league = new League(new LeagueId(UUID.randomUUID()), "Championship standings", new LeagueSource.Manual(),
                new LeagueSettings(null, false, firstSeason), EARLIER);
        transactions.executeWithoutResult(status -> leagues.create(league));
        return league;
    }

    private LeagueParticipant createParticipant(League league, String displayName) {
        var identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        return participants.ensureParticipant(league.id(), identity, displayName);
    }
}
