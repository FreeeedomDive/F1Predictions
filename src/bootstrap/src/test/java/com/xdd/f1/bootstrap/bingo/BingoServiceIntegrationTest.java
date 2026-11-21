package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoBoardDetails;
import com.xdd.f1.application.bingo.BingoBoardRepository;
import com.xdd.f1.application.bingo.BingoCardNotFoundException;
import com.xdd.f1.application.bingo.BingoCardRepository;
import com.xdd.f1.application.bingo.BingoConflictException;
import com.xdd.f1.application.bingo.BingoService;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.contract.bingo.BingoAchievedEvent;
import com.xdd.f1.domain.bingo.BingoBoard;
import com.xdd.f1.domain.bingo.BingoBoardId;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.bingo.BingoCardProbability;
import com.xdd.f1.domain.bingo.BingoCardTranslation;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.infrastructure.persistence.bingo.BingoBoardJpaRepository;
import com.xdd.f1.infrastructure.persistence.bingo.BingoCardJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@RecordApplicationEvents
class BingoServiceIntegrationTest extends AbstractIntegrationTest {
    private static final int SEASON = 2027;
    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");
    private final BingoService service;
    private final BingoCardRepository cards;
    private final BingoBoardRepository boards;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final BingoCardJpaRepository cardEntities;
    private final BingoBoardJpaRepository boardEntities;
    private final TransactionTemplate transactions;

    @MockitoBean
    private Clock clock;

    private LeagueParticipant alice;

    @Autowired
    BingoServiceIntegrationTest(BingoService service, BingoCardRepository cards, BingoBoardRepository boards,
                                LeagueRepository leagues, LeagueParticipantService participants,
                                BingoCardJpaRepository cardEntities, BingoBoardJpaRepository boardEntities,
                                PlatformTransactionManager transactionManager) {
        this.service = service;
        this.cards = cards;
        this.boards = boards;
        this.leagues = leagues;
        this.participants = participants;
        this.cardEntities = cardEntities;
        this.boardEntities = boardEntities;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.ofHours(-5));
        when(clock.withZone(ZoneOffset.UTC)).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        alice = participant(SEASON - 1, "Alice");
    }

    @AfterEach
    void cleanUp() {
        boardEntities.deleteAll();
        cardEntities.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 24, 26})
    void doesNotCreateAnEmptyOrIncompleteBoard(int count, ApplicationEvents events) {
        createCards(SEASON, count);

        assertThat(find(alice, SEASON)).isEmpty();
        assertThatThrownBy(() -> create(alice, SEASON)).isInstanceOf(BingoConflictException.class);
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
        assertThat(events.stream(BingoAchievedEvent.class)).isEmpty();
    }

    @Test
    void createsOwnBoardOnceAndReturnsCardsInItsSavedOrder(ApplicationEvents events) {
        var seasonCards = createCards(SEASON, 25);
        var bob = participants.ensureParticipant(alice.leagueId(), identity(), "Bob");
        assertThat(find(alice, SEASON)).isEmpty();

        var created = create(alice, SEASON);
        assertThat(created.board().participantId()).isEqualTo(alice.id());
        assertThat(created.board().leagueId()).isEqualTo(alice.leagueId());
        assertThat(created.board().cards()).containsExactlyInAnyOrderElementsOf(seasonCards.stream().map(BingoCard::id).toList());
        assertThat(created.cards()).extracting(BingoCard::id).containsExactlyElementsOf(created.board().cards());
        assertThat(created.completedLines()).isEmpty();
        assertThat(created.board().bingoAchieved()).isFalse();
        assertThat(create(alice, SEASON)).isEqualTo(created);
        assertThat(find(alice, SEASON)).contains(created);
        assertThat(find(bob, SEASON)).isEmpty();
        assertThat(boards.findAllBySeason(SEASON)).containsExactly(created.board());
        assertThat(events.stream(BingoAchievedEvent.class)).isEmpty();
    }

    @Test
    void rejectsUsersOutsideTheLeagueWithoutCreatingParticipation() {
        createCards(SEASON, 25);
        var foreign = participant(SEASON, "Foreign participant");
        var unknown = new UserId(UUID.randomUUID());

        for (var userId : List.of(foreign.userId(), unknown)) {
            assertThatThrownBy(() -> service.getOwn(userId, alice.leagueId(), Optional.of(SEASON)))
                    .isInstanceOf(LeagueParticipantNotFoundException.class);
            assertThatThrownBy(() -> service.createOwn(userId, alice.leagueId(), Optional.of(SEASON)))
                    .isInstanceOf(LeagueParticipantNotFoundException.class);
        }
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void readsExistingArchivesButCreatesOnlyCurrentBoards(ApplicationEvents events) {
        var archivedCards = createCards(SEASON - 1, 25);
        var archive = seedBoard(alice, SEASON - 1, archivedCards.stream().map(BingoCard::id).toList().reversed(), true);
        var bob = participants.ensureParticipant(alice.leagueId(), identity(), "Bob");
        createCards(SEASON + 1, 25);

        var details = find(alice, SEASON - 1).orElseThrow();
        assertThat(details.board()).isEqualTo(archive);
        assertThat(details.cards()).extracting(BingoCard::id).containsExactlyElementsOf(archive.cards());
        assertThat(details.completedLines()).isEmpty();
        assertThat(create(alice, SEASON - 1)).isEqualTo(details);
        assertThat(find(bob, SEASON - 1)).isEmpty();
        assertThatThrownBy(() -> create(bob, SEASON - 1)).isInstanceOf(BingoConflictException.class);
        assertThatThrownBy(() -> create(alice, SEASON + 1)).isInstanceOf(BingoConflictException.class);
        assertThat(events.stream(BingoAchievedEvent.class)).isEmpty();
    }

    @Test
    void respectsTheLeaguesFirstSeason() {
        createCards(SEASON, 25);
        var future = participant(SEASON + 1, "Future participant");

        assertThat(find(future, SEASON)).isEmpty();
        assertThatThrownBy(() -> create(future, SEASON)).isInstanceOf(BingoConflictException.class);
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void fixesFirstBingoAcrossLeaguesAndKeepsItAfterCorrections(ApplicationEvents events) {
        var seasonCards = createCards(SEASON, 25);
        var bob = participant(SEASON, "Bob");
        var order = seasonCards.stream().map(BingoCard::id).toList();
        var first = seedBoard(alice, SEASON, order, false);
        var second = seedBoard(bob, SEASON, order.reversed(), false);
        complete(seasonCards.subList(0, 4));
        var last = seasonCards.get(4);

        assertThat(service.updateProgress(last.id(), 1)).isEqualTo(last.updateProgress(1));
        assertThat(find(alice, SEASON).orElseThrow().board().bingoAchieved()).isFalse();
        assertThat(events.stream(BingoAchievedEvent.class)).isEmpty();
        service.updateProgress(last.id(), 2);

        assertThat(find(alice, SEASON).orElseThrow().completedLines()).containsExactly(List.of(0, 1, 2, 3, 4));
        assertThat(find(bob, SEASON).orElseThrow().completedLines()).containsExactly(List.of(20, 21, 22, 23, 24));
        assertThat(events.stream(BingoAchievedEvent.class)).containsExactlyInAnyOrder(
                new BingoAchievedEvent(alice.leagueId().value(), SEASON, first.id().value(), alice.id().value(), "Alice"),
                new BingoAchievedEvent(bob.leagueId().value(), SEASON, second.id().value(), bob.id().value(), "Bob"));

        service.updateProgress(last.id(), 2);
        service.updateProgress(last.id(), 0);
        var corrected = find(alice, SEASON).orElseThrow();
        assertThat(corrected.completedLines()).isEmpty();
        assertThat(corrected.board().bingoAchieved()).isTrue();
        assertThat(corrected.board().cards()).containsExactlyElementsOf(first.cards());
        service.updateProgress(last.id(), 2);
        assertThat(find(bob, SEASON).orElseThrow().board().bingoAchieved()).isTrue();
        assertThat(events.stream(BingoAchievedEvent.class).count()).isEqualTo(2);
    }

    @Test
    void checksAlreadyCompletedCardsOnFirstCreation(ApplicationEvents events) {
        var seasonCards = createCards(SEASON, 25);
        complete(seasonCards);

        var created = create(alice, SEASON);
        assertThat(created.board().bingoAchieved()).isTrue();
        assertThat(created.completedLines()).hasSize(12);
        assertThat(create(alice, SEASON)).isEqualTo(created);
        assertThat(events.stream(BingoAchievedEvent.class)).containsExactly(new BingoAchievedEvent(
                alice.leagueId().value(), SEASON, created.board().id().value(), alice.id().value(), "Alice"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 3})
    void rejectsInvalidProgressWithoutChangingCardsOrBoards(int repeats, ApplicationEvents events) {
        var seasonCards = createCards(SEASON, 25);
        var created = create(alice, SEASON);
        var first = seasonCards.getFirst();

        assertThatThrownBy(() -> service.updateProgress(first.id(), repeats)).isInstanceOf(DomainValidationException.class);
        assertThat(cards.findById(first.id())).contains(first);
        assertThat(find(alice, SEASON)).contains(created);
        assertThat(events.stream(BingoAchievedEvent.class)).isEmpty();
    }

    @Test
    void rejectsMissingAndArchivedCardsOnProgressUpdate() {
        var archived = createCards(SEASON - 1, 1).getFirst();

        assertThatThrownBy(() -> service.updateProgress(new BingoCardId(UUID.randomUUID()), 1))
                .isInstanceOf(BingoCardNotFoundException.class);
        assertThatThrownBy(() -> service.updateProgress(archived.id(), 1)).isInstanceOf(DomainValidationException.class);
        assertThat(cards.findById(archived.id())).contains(archived);
    }

    @Test
    void rollsBackProgressAndAchievementTogether() {
        var seasonCards = createCards(SEASON, 25);
        var board = seedBoard(alice, SEASON, seasonCards.stream().map(BingoCard::id).toList(), false);
        complete(seasonCards.subList(0, 4));
        var last = seasonCards.get(4);

        transactions.executeWithoutResult(status -> {
            service.updateProgress(last.id(), 2);
            assertThat(find(alice, SEASON).orElseThrow().board().bingoAchieved()).isTrue();
            status.setRollbackOnly();
        });

        assertThat(cards.findById(last.id())).contains(last);
        assertThat(find(alice, SEASON).orElseThrow().board()).isEqualTo(board);
    }

    @Test
    void serializesConcurrentFirstOpeningsWithoutReshuffling() throws Exception {
        createCards(SEASON, 25);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var started = new CountDownLatch(1);
            var waiting = transactions.execute(status -> {
                var first = create(alice, SEASON);
                var future = executor.submit(() -> {
                    started.countDown();
                    return create(alice, SEASON);
                });
                await(started);
                assertThat(future.isDone()).isFalse();
                assertThat(find(alice, SEASON)).contains(first);
                return future;
            });

            var returned = waiting.get(10, TimeUnit.SECONDS);
            assertThat(find(alice, SEASON)).contains(returned);
            assertThat(boards.findAllBySeason(SEASON)).containsExactly(returned.board());
        }
    }

    @Test
    void serializesProgressUpdatesWithFirstBoardCreation() throws Exception {
        var seasonCards = createCards(SEASON, 25);
        var changed = seasonCards.getFirst();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var started = new CountDownLatch(1);
            var waiting = transactions.execute(status -> {
                service.updateProgress(changed.id(), 2);
                var future = executor.submit(() -> {
                    started.countDown();
                    return create(alice, SEASON);
                });
                await(started);
                assertThat(future.isDone()).isFalse();
                return future;
            });

            var created = waiting.get(10, TimeUnit.SECONDS);
            assertThat(created.cards()).contains(changed.updateProgress(2));
            assertThat(find(alice, SEASON)).contains(created);
        }
    }

    @Test
    void usesTheUtcYearAtTheSeasonBoundary() {
        when(clock.instant()).thenReturn(Instant.parse("2027-01-01T00:30:00Z"));
        when(clock.withZone(ZoneOffset.UTC))
                .thenReturn(Clock.fixed(Instant.parse("2027-01-01T00:30:00Z"), ZoneOffset.UTC));
        createCards(SEASON, 25);

        assertThat(create(alice, SEASON).board().season()).isEqualTo(SEASON);
    }

    private Optional<BingoBoardDetails> find(LeagueParticipant participant, int season) {
        return Optional.ofNullable(service.getOwn(participant.userId(), participant.leagueId(), Optional.of(season)).board());
    }

    private BingoBoardDetails create(LeagueParticipant participant, int season) {
        return service.createOwn(participant.userId(), participant.leagueId(), Optional.of(season)).board();
    }

    private List<BingoCard> createCards(int season, int count) {
        var result = IntStream.range(0, count).mapToObj(i -> new BingoCard(new BingoCardId(UUID.randomUUID()),
                season, Map.of("en", new BingoCardTranslation("Event " + i, null)), BingoCardProbability.MEDIUM, 2, 0)).toList();
        transactions.executeWithoutResult(status -> result.forEach(cards::create));
        return result;
    }

    private BingoBoard seedBoard(LeagueParticipant participant, int season, List<BingoCardId> order, boolean achieved) {
        var board = new BingoBoard(new BingoBoardId(UUID.randomUUID()), participant.leagueId(),
                season, participant.id(), order, achieved);
        transactions.executeWithoutResult(status -> boards.create(board));
        return board;
    }

    private void complete(List<BingoCard> toComplete) {
        transactions.executeWithoutResult(status -> toComplete.forEach(card -> cards.updateProgress(card.id(), card.requiredRepeats())));
    }

    private LeagueParticipant participant(int firstSeason, String name) {
        var league = new League(new LeagueId(UUID.randomUUID()), "Bingo test league", new LeagueSource.Manual(),
                new LeagueSettings(null, false, firstSeason), NOW);
        transactions.executeWithoutResult(status -> leagues.create(league));
        return participants.ensureParticipant(league.id(), identity(), name);
    }

    private UserIdentityId identity() {
        return new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
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
