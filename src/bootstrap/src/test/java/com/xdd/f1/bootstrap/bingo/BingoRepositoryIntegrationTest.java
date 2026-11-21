package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoBoardNotFoundException;
import com.xdd.f1.application.bingo.BingoBoardRepository;
import com.xdd.f1.application.bingo.BingoCardNotFoundException;
import com.xdd.f1.application.bingo.BingoCardRepository;
import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.bingo.BingoBoard;
import com.xdd.f1.domain.bingo.BingoBoardId;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.bingo.BingoCardProbability;
import com.xdd.f1.domain.bingo.BingoCardTranslation;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class BingoRepositoryIntegrationTest extends AbstractIntegrationTest {
    private static final int SEASON = 9991;
    private static final Map<String, BingoCardTranslation> TRANSLATIONS = Map.of(
            "en", new BingoCardTranslation("Event", "Condition"),
            "ru", new BingoCardTranslation("Событие", "Условие"),
            "de", new BingoCardTranslation("Ereignis", null));
    private final BingoCardRepository cards;
    private final BingoBoardRepository boards;
    private final LeagueService leagues;
    private final LeagueParticipantService participants;
    private final EntityManager entityManager;

    @Autowired
    BingoRepositoryIntegrationTest(
            BingoCardRepository cards, BingoBoardRepository boards, LeagueService leagues,
            LeagueParticipantService participants, EntityManager entityManager
    ) {
        this.cards = cards;
        this.boards = boards;
        this.leagues = leagues;
        this.participants = participants;
        this.entityManager = entityManager;
    }

    @Test
    void storesCardsWithTranslationsAndProgressAndFiltersBySeason() {
        var first = card(SEASON, BingoCardProbability.LOW, 3, 1, TRANSLATIONS);
        var second = card(SEASON, BingoCardProbability.HIGH, 1, 1,
                Map.of("en", new BingoCardTranslation("Another event", null)));
        var otherSeason = card(SEASON + 1, BingoCardProbability.MEDIUM, 2, 0, TRANSLATIONS);
        cards.create(first);
        cards.create(second);
        cards.create(otherSeason);
        flushAndClear();

        assertThat(cards.findById(first.id())).contains(first);
        assertThat(cards.findById(second.id())).contains(second);
        assertThat(cards.findAllBySeason(SEASON)).containsExactlyInAnyOrder(first, second);
        assertThat(cards.findAllBySeason(SEASON + 1)).containsExactly(otherSeason);
    }

    @Test
    void updatesOnlyTheSelectedCardsProgressAndAllowsCorrection() {
        var first = card(SEASON, BingoCardProbability.MEDIUM, 3, 0, TRANSLATIONS);
        var second = card(SEASON, BingoCardProbability.LOW, 2, 1, TRANSLATIONS);
        cards.create(first);
        cards.create(second);
        flushAndClear();

        cards.updateProgress(first.id(), 3);
        flushAndClear();
        assertThat(cards.findById(first.id())).contains(first.updateProgress(3));
        assertThat(cards.findById(second.id())).contains(second);

        cards.updateProgress(first.id(), 1);
        flushAndClear();
        assertThat(cards.findById(first.id())).contains(first.updateProgress(1));
        assertThat(cards.findById(second.id())).contains(second);
    }

    @Test
    void readsMissingCardsAndBoardsWithoutCreatingAnything() {
        var participant = participant();

        assertThat(cards.findById(new BingoCardId(UUID.randomUUID()))).isEmpty();
        assertThat(cards.findAllBySeason(SEASON)).isEmpty();
        assertThat(findBoard(participant, SEASON)).isEmpty();
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
        flushAndClear();
        assertThat(findBoard(participant, SEASON)).isEmpty();
    }

    @Test
    void storesIndependentBoardOrdersAndHistoricalAchievementsUsingGlobalCards() {
        var firstParticipant = participant();
        var secondParticipant = participant();
        var currentCards = createCards(SEASON);
        var nextCards = createCards(SEASON + 1);
        var order = IntStream.range(0, 25).mapToObj(i -> currentCards.get((i * 7) % 25).id()).toList();
        var first = board(firstParticipant, SEASON, order, true);
        var second = board(secondParticipant, SEASON, order.reversed(), false);
        var next = board(firstParticipant, SEASON + 1, nextCards.stream().map(BingoCard::id).toList(), false);
        boards.create(first);
        boards.create(second);
        boards.create(next);
        flushAndClear();

        assertThat(findBoard(firstParticipant, SEASON)).contains(first);
        assertThat(findBoard(secondParticipant, SEASON)).contains(second);
        assertThat(findBoard(firstParticipant, SEASON + 1)).contains(next);
        assertThat(boards.findAllBySeason(SEASON)).containsExactlyInAnyOrder(first, second);
        assertThat(boards.findAllBySeason(SEASON + 1)).containsExactly(next);
        assertThat(boards.findByLeagueAndSeasonAndParticipant(
                firstParticipant.leagueId(), SEASON, secondParticipant.id())).isEmpty();
        assertThat(findBoard(firstParticipant, SEASON).orElseThrow().updateAchievement(Set.of())).isEqualTo(first);
    }

    @Test
    void marksAchievementWithoutChangingTheLayoutOrOtherBoards() {
        var firstParticipant = participant();
        var secondParticipant = participants.ensureParticipant(firstParticipant.leagueId(),
                identity(), "Second participant");
        var currentCards = createCards(SEASON);
        var order = currentCards.stream().map(BingoCard::id).toList();
        var first = board(firstParticipant, SEASON, order, false);
        var second = board(secondParticipant, SEASON, order.reversed(), false);
        boards.create(first);
        boards.create(second);
        flushAndClear();

        boards.markAchieved(first.id());
        flushAndClear();
        var achieved = boardWithAchievement(first);
        assertThat(findBoard(firstParticipant, SEASON)).contains(achieved);
        assertThat(findBoard(secondParticipant, SEASON)).contains(second);

        boards.markAchieved(first.id());
        cards.updateProgress(currentCards.getFirst().id(), 1);
        flushAndClear();
        assertThat(findBoard(firstParticipant, SEASON)).contains(achieved);
        assertThat(findBoard(secondParticipant, SEASON)).contains(second);
        assertThat(cards.findById(currentCards.getFirst().id())).contains(currentCards.getFirst().updateProgress(1));
    }

    @Test
    void reportsMissingCardsAndBoardsOnUpdate() {
        var cardId = new BingoCardId(UUID.randomUUID());
        var boardId = new BingoBoardId(UUID.randomUUID());

        assertThatThrownBy(() -> cards.updateProgress(cardId, 1)).isInstanceOf(BingoCardNotFoundException.class);
        assertThatThrownBy(() -> boards.markAchieved(boardId)).isInstanceOf(BingoBoardNotFoundException.class);
    }

    private Optional<BingoBoard> findBoard(LeagueParticipant participant, int season) {
        return boards.findByLeagueAndSeasonAndParticipant(participant.leagueId(), season, participant.id());
    }

    private List<BingoCard> createCards(int season) {
        var result = IntStream.range(0, 25)
                .mapToObj(i -> card(season, BingoCardProbability.MEDIUM, 3, 0, TRANSLATIONS)).toList();
        result.forEach(cards::create);
        return result;
    }

    private BingoCard card(int season, BingoCardProbability probability, int required, int completed,
                           Map<String, BingoCardTranslation> translations) {
        return new BingoCard(new BingoCardId(UUID.randomUUID()), season, translations, probability, required, completed);
    }

    private BingoBoard board(LeagueParticipant participant, int season, List<BingoCardId> order, boolean achieved) {
        return new BingoBoard(new BingoBoardId(UUID.randomUUID()), participant.leagueId(),
                season, participant.id(), order, achieved);
    }

    private BingoBoard boardWithAchievement(BingoBoard board) {
        return new BingoBoard(board.id(), board.leagueId(), board.season(), board.participantId(), board.cards(), true);
    }

    private LeagueParticipant participant() {
        var league = leagues.createManual("Bingo test league");
        return participants.ensureParticipant(league.id(), identity(), "Participant");
    }

    private UserIdentityId identity() {
        return new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
