package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoBoardRepository;
import com.xdd.f1.application.bingo.BingoCardRepository;
import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.contract.bingo.BingoAchievedEvent;
import com.xdd.f1.contract.bingo.BingoCardProbabilityDto;
import com.xdd.f1.contract.bingo.BingoCardTranslationDto;
import com.xdd.f1.contract.bingo.ImportBingoBoardsRequestDto;
import com.xdd.f1.contract.bingo.ImportBingoCardsRequestDto;
import com.xdd.f1.domain.bingo.BingoBoard;
import com.xdd.f1.domain.bingo.BingoBoardId;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.infrastructure.persistence.bingo.BingoBoardJpaRepository;
import com.xdd.f1.infrastructure.persistence.bingo.BingoCardJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@RecordApplicationEvents
@TestPropertySource(properties = "imports.api-key=bingo-import-test-key")
class BingoImportApiIntegrationTest extends AbstractIntegrationTest {
    private static final int SEASON = 2025;
    private static final String KEY = "bingo-import-test-key";
    private static final String KEY_HEADER = "X-Import-Api-Key";
    private final MockMvc mvc;
    private final JsonMapper json;
    private final BingoCardRepository cards;
    private final BingoBoardRepository boards;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final BingoCardJpaRepository cardEntities;
    private final BingoBoardJpaRepository boardEntities;
    private final TransactionTemplate transactions;

    private LeagueParticipant alice;

    @Autowired
    BingoImportApiIntegrationTest(MockMvc mvc, JsonMapper json, BingoCardRepository cards, BingoBoardRepository boards,
                                  LeagueRepository leagues, LeagueParticipantService participants,
                                  BingoCardJpaRepository cardEntities, BingoBoardJpaRepository boardEntities,
                                  PlatformTransactionManager transactionManager) {
        this.mvc = mvc;
        this.json = json;
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
        var league = new League(new LeagueId(UUID.randomUUID()), "Bingo import league", new LeagueSource.Manual(),
                new LeagueSettings(null, false, SEASON - 1), Instant.parse("2024-01-01T00:00:00Z"));
        transactions.executeWithoutResult(status -> leagues.create(league));
        alice = participants.ensureParticipant(league.id(), identity(), "Alice");
    }

    @AfterEach
    void cleanUp() {
        boardEntities.deleteAll();
        cardEntities.deleteAll();
    }

    @Test
    void requiresTheImportKeyOnBothRoutes() throws Exception {
        for (var url : List.of(cardsUrl(SEASON), boardsUrl(alice.leagueId(), SEASON))) {
            mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
            mvc.perform(put(url).header(KEY_HEADER, "wrong").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(cards.findAllBySeason(SEASON)).isEmpty();
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void importsCardsWithTheirIdsTranslationsAndProgressAndAllowsAnIdenticalRetry(ApplicationEvents events) throws Exception {
        var imported = cardRequest(25, 5);
        upload(cardsUrl(SEASON), imported).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.created").value(25)).andExpect(jsonPath("$.unchanged").value(0));
        var stored = cards.findAllBySeason(SEASON);
        assertThat(stored).hasSize(25);
        for (var source : imported.cards()) {
            var card = cards.findById(new BingoCardId(source.id())).orElseThrow();
            assertThat(card.season()).isEqualTo(SEASON);
            assertThat(card.translations().get("en").description()).isEqualTo(source.translations().get("EN").description());
            assertThat(card.translations().get("ru").explanation()).isEqualTo("Условие");
            assertThat(card.probability().name()).isEqualTo(source.probability().name());
            assertThat(card.requiredRepeats()).isEqualTo(source.requiredRepeats());
            assertThat(card.completedRepeats()).isEqualTo(source.completedRepeats());
        }
        upload(cardsUrl(SEASON), imported).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.unchanged").value(25));
        assertThat(cards.findAllBySeason(SEASON)).containsExactlyInAnyOrderElementsOf(stored);
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
        assertThat(events.stream(BingoAchievedEvent.class)).isEmpty();
    }

    @Test
    void rejectsConflictingCardsWithoutCreatingOtherCardsInTheRequest() throws Exception {
        var original = cardRequest(1, 0);
        upload(cardsUrl(SEASON), original).andExpect(status().isOk());
        var existing = original.cards().getFirst();
        var changed = new ImportBingoCardsRequestDto.Card(existing.id(), existing.translations(),
                existing.probability(), existing.requiredRepeats(), 2);
        var request = new ImportBingoCardsRequestDto(List.of(cardRequest(1, 0).cards().getFirst(), changed));
        upload(cardsUrl(SEASON), request).andExpect(status().isConflict());
        assertThat(cards.findAllBySeason(SEASON)).hasSize(1);
        assertThat(cards.findById(new BingoCardId(existing.id())).orElseThrow().completedRepeats()).isZero();
        upload(cardsUrl(SEASON + 1), original).andExpect(status().isConflict());
        assertThat(cards.findAllBySeason(SEASON + 1)).isEmpty();
    }

    @Test
    void rejectsDuplicateCardsAndASeasonExceeding25Cards() throws Exception {
        var single = cardRequest(1, 0);
        upload(cardsUrl(SEASON), new ImportBingoCardsRequestDto(
                List.of(single.cards().getFirst(), single.cards().getFirst()))).andExpect(status().isBadRequest());
        upload(cardsUrl(SEASON), single).andExpect(status().isOk());
        upload(cardsUrl(SEASON), cardRequest(25, 0)).andExpect(status().isConflict());
        assertThat(cards.findAllBySeason(SEASON)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"cards\":[]}", "{\"cards\":[null]}", "{\"cards\":[{}]}",
            "{\"cards\":[{\"id\":\"bad\"}]}",
            "{\"cards\":[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"translations\":{\"ru\":{\"description\":\"Текст\"}},\"probability\":\"LOW\",\"requiredRepeats\":1,\"completedRepeats\":0}]}",
            "{\"cards\":[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"translations\":{\"en\":null},\"probability\":\"LOW\",\"requiredRepeats\":1,\"completedRepeats\":0}]}",
            "{\"cards\":[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"translations\":{\"en\":{\"description\":\"Event\"}},\"probability\":\"LOW\",\"requiredRepeats\":1,\"completedRepeats\":2}]}",
            "{\"cards\":[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"translations\":{\"en_US\":{\"description\":\"Event\"}},\"probability\":\"LOW\",\"requiredRepeats\":1,\"completedRepeats\":0}]}"
    })
    void rejectsInvalidCardPayloads(String payload) throws Exception {
        mvc.perform(put(cardsUrl(SEASON)).header(KEY_HEADER, KEY).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        assertThat(cards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void rejectsNonPositiveSeasons() throws Exception {
        upload(cardsUrl(0), cardRequest(1, 0)).andExpect(status().isBadRequest());
        var order = cardRequest(25, 0).cards().stream().map(ImportBingoCardsRequestDto.Card::id).toList();
        upload(boardsUrl(alice.leagueId(), -1), boardRequest(alice, order, false)).andExpect(status().isBadRequest());
    }

    @Test
    void importsBoardOrderAndHistoricalAchievementWithoutPublishingEvents(ApplicationEvents events) throws Exception {
        var imported = cardRequest(25, 0);
        upload(cardsUrl(SEASON), imported).andExpect(status().isOk());
        var order = new ArrayList<>(imported.cards().stream().map(ImportBingoCardsRequestDto.Card::id).toList());
        Collections.rotate(order, 7);
        var request = boardRequest(alice, order, true);
        var source = request.boards().getFirst();
        upload(boardsUrl(alice.leagueId(), SEASON), request).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.unchanged").value(0));
        var stored = boards.findById(new BingoBoardId(source.id())).orElseThrow();
        assertThat(stored.leagueId()).isEqualTo(alice.leagueId());
        assertThat(stored.season()).isEqualTo(SEASON);
        assertThat(stored.participantId()).isEqualTo(alice.id());
        assertThat(stored.cards()).extracting(BingoCardId::value).containsExactlyElementsOf(order);
        assertThat(stored.bingoAchieved()).isTrue();
        upload(boardsUrl(alice.leagueId(), SEASON), request).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.unchanged").value(1));
        assertThat(boards.findAllBySeason(SEASON)).containsExactly(stored);
        upload(cardsUrl(SEASON), imported).andExpect(status().isOk()).andExpect(jsonPath("$.unchanged").value(25));
        upload(cardsUrl(SEASON), cardRequest(1, 0)).andExpect(status().isConflict());
        assertThat(events.stream(BingoAchievedEvent.class)).isEmpty();
    }

    @Test
    void importsAnAchievedBoardWithACompletedLineButRejectsAnUnrecordedBingo(ApplicationEvents events) throws Exception {
        var imported = cardRequest(25, 5);
        upload(cardsUrl(SEASON), imported).andExpect(status().isOk());
        var order = imported.cards().stream().map(ImportBingoCardsRequestDto.Card::id).toList();
        upload(boardsUrl(alice.leagueId(), SEASON), boardRequest(alice, order, false)).andExpect(status().isConflict());
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
        upload(boardsUrl(alice.leagueId(), SEASON), boardRequest(alice, order, true)).andExpect(status().isOk());
        assertThat(boards.findAllBySeason(SEASON)).singleElement().extracting(BingoBoard::bingoAchieved).isEqualTo(true);
        assertThat(events.stream(BingoAchievedEvent.class)).isEmpty();
    }

    @Test
    void requiresAllCardsAndExistingLeagueParticipationBeforeImportingBoards() throws Exception {
        var imported = cardRequest(25, 0);
        var order = imported.cards().stream().map(ImportBingoCardsRequestDto.Card::id).toList();
        var request = boardRequest(alice, order, false);
        upload(boardsUrl(alice.leagueId(), SEASON), request).andExpect(status().isConflict());
        upload(cardsUrl(SEASON), imported).andExpect(status().isOk());
        upload(boardsUrl(new LeagueId(UUID.randomUUID()), SEASON), request).andExpect(status().isNotFound());
        var unknownParticipant = new ImportBingoBoardsRequestDto.Board(UUID.randomUUID(), UUID.randomUUID(), order, false);
        upload(boardsUrl(alice.leagueId(), SEASON), new ImportBingoBoardsRequestDto(List.of(unknownParticipant)))
                .andExpect(status().isNotFound());
        upload(boardsUrl(alice.leagueId(), SEASON - 2), request).andExpect(status().isConflict());
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void rejectsMissingForeignAndDuplicateBoardCards() throws Exception {
        var imported = cardRequest(25, 0);
        upload(cardsUrl(SEASON), imported).andExpect(status().isOk());
        var order = new ArrayList<>(imported.cards().stream().map(ImportBingoCardsRequestDto.Card::id).toList());
        var foreign = cardRequest(1, 0);
        upload(cardsUrl(SEASON + 1), foreign).andExpect(status().isOk());
        var original = order.getFirst();
        order.set(0, foreign.cards().getFirst().id());
        upload(boardsUrl(alice.leagueId(), SEASON), boardRequest(alice, order, false)).andExpect(status().isConflict());
        order.set(0, UUID.randomUUID());
        upload(boardsUrl(alice.leagueId(), SEASON), boardRequest(alice, order, false)).andExpect(status().isConflict());
        order.set(0, order.get(1));
        upload(boardsUrl(alice.leagueId(), SEASON), boardRequest(alice, order, false)).andExpect(status().isBadRequest());
        order.set(0, original);
        order.removeLast();
        upload(boardsUrl(alice.leagueId(), SEASON), boardRequest(alice, order, false)).andExpect(status().isBadRequest());
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void rejectsBoardConflictsWithoutImportingOtherBoardsInTheRequest() throws Exception {
        var imported = cardRequest(25, 0);
        upload(cardsUrl(SEASON), imported).andExpect(status().isOk());
        var order = imported.cards().stream().map(ImportBingoCardsRequestDto.Card::id).toList();
        var original = boardRequest(alice, order, false);
        upload(boardsUrl(alice.leagueId(), SEASON), original).andExpect(status().isOk());
        var bob = participants.ensureParticipant(alice.leagueId(), identity(), "Bob");
        var next = boardRequest(bob, order, false).boards().getFirst();
        var changed = boardRequest(alice, order, false).boards().getFirst();
        upload(boardsUrl(alice.leagueId(), SEASON), new ImportBingoBoardsRequestDto(List.of(next, changed)))
                .andExpect(status().isConflict());
        var sameIdDifferentOwner = new ImportBingoBoardsRequestDto.Board(original.boards().getFirst().id(),
                bob.id().value(), order, false);
        upload(boardsUrl(alice.leagueId(), SEASON), new ImportBingoBoardsRequestDto(List.of(sameIdDifferentOwner)))
                .andExpect(status().isConflict());
        var reversed = new ArrayList<>(order);
        Collections.reverse(reversed);
        var sameIdDifferentOrder = new ImportBingoBoardsRequestDto.Board(original.boards().getFirst().id(),
                alice.id().value(), reversed, false);
        upload(boardsUrl(alice.leagueId(), SEASON), new ImportBingoBoardsRequestDto(List.of(sameIdDifferentOrder)))
                .andExpect(status().isConflict());
        assertThat(boards.findAllBySeason(SEASON)).singleElement()
                .extracting(board -> board.id().value()).isEqualTo(original.boards().getFirst().id());
    }

    @Test
    void rejectsDuplicateBoardIdsAndOwners() throws Exception {
        var imported = cardRequest(25, 0);
        upload(cardsUrl(SEASON), imported).andExpect(status().isOk());
        var order = imported.cards().stream().map(ImportBingoCardsRequestDto.Card::id).toList();
        var single = boardRequest(alice, order, false).boards().getFirst();
        upload(boardsUrl(alice.leagueId(), SEASON), new ImportBingoBoardsRequestDto(List.of(single, single)))
                .andExpect(status().isBadRequest());
        upload(boardsUrl(alice.leagueId(), SEASON), new ImportBingoBoardsRequestDto(
                List.of(single, boardRequest(alice, order, false).boards().getFirst()))).andExpect(status().isBadRequest());
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"boards\":[]}", "{\"boards\":[null]}", "{\"boards\":[{}]}",
            "{\"boards\":[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"participantId\":\"00000000-0000-0000-0000-000000000002\",\"cardIds\":[null],\"bingoAchieved\":false}]}"})
    void rejectsIncompleteBoardPayloads(String payload) throws Exception {
        mvc.perform(put(boardsUrl(alice.leagueId(), SEASON)).header(KEY_HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    private ImportBingoCardsRequestDto cardRequest(int count, int completed) {
        return new ImportBingoCardsRequestDto(IntStream.range(0, count).mapToObj(index ->
                new ImportBingoCardsRequestDto.Card(UUID.randomUUID(), Map.of(
                        "EN", new BingoCardTranslationDto("Event " + index, "Condition"),
                        "ru", new BingoCardTranslationDto("Событие " + index, "Условие")),
                        BingoCardProbabilityDto.MEDIUM, 3, index < completed ? 3 : 0)).toList());
    }

    private ImportBingoBoardsRequestDto boardRequest(LeagueParticipant participant, List<UUID> order, boolean achieved) {
        return new ImportBingoBoardsRequestDto(List.of(new ImportBingoBoardsRequestDto.Board(
                UUID.randomUUID(), participant.id().value(), List.copyOf(order), achieved)));
    }

    private ResultActions upload(String url, Object body) throws Exception {
        return mvc.perform(put(url).header(KEY_HEADER, KEY).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    private UserIdentityId identity() {
        return new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
    }

    private String cardsUrl(int season) {
        return "/importApi/v1/bingo/" + season + "/cards";
    }

    private String boardsUrl(LeagueId leagueId, int season) {
        return "/importApi/v1/leagues/" + leagueId.value() + "/bingo/" + season + "/boards";
    }
}
