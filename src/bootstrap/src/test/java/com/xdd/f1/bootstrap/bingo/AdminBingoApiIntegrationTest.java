package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoAdminService;
import com.xdd.f1.application.bingo.BingoBoardRepository;
import com.xdd.f1.application.bingo.BingoCardRepository;
import com.xdd.f1.application.bingo.BingoConflictException;
import com.xdd.f1.application.bingo.BingoService;
import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.application.rights.RightsService;
import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.bootstrap.auth.SessionCookie;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.contract.bingo.BingoAchievedEvent;
import com.xdd.f1.contract.bingo.BingoAdminCardDto;
import com.xdd.f1.contract.bingo.BingoAdminDetailsDto;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.bingo.BingoCardProbability;
import com.xdd.f1.domain.bingo.BingoCardTranslation;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.infrastructure.persistence.bingo.BingoBoardJpaRepository;
import com.xdd.f1.infrastructure.persistence.bingo.BingoCardJpaRepository;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@RecordApplicationEvents
@TestPropertySource(properties = "internal-api.service-key=bingo-test-key")
class AdminBingoApiIntegrationTest extends AbstractIntegrationTest {
    private static final int SEASON = 2027;
    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");
    private static final String ADMIN = "/adminApi/v1/bingo";
    private static final String SAVE_CARD = """
            {"translations":{"en":{"description":"Event","explanation":null}},
             "probability":"MEDIUM","requiredRepeats":3}
            """;
    private static final Map<String, BingoCardTranslation> TRANSLATIONS = Map.of(
            "en", new BingoCardTranslation("Event", "Condition"),
            "ru", new BingoCardTranslation("Событие", "Условие"),
            "de", new BingoCardTranslation("Ereignis", null));
    private final MockMvc mvc;
    private final JsonMapper json;
    private final BingoAdminService admin;
    private final BingoService bingo;
    private final BingoCardRepository cards;
    private final BingoBoardRepository boards;
    private final BingoCardJpaRepository cardEntities;
    private final BingoBoardJpaRepository boardEntities;
    private final LeagueService leagues;
    private final LeagueParticipantService participants;
    private final RightsService rights;
    private final SessionService sessions;
    private final TransactionTemplate transactions;

    @MockitoBean
    private Clock clock;

    private LeagueParticipant administrator;
    private Cookie cookie;

    @Autowired
    AdminBingoApiIntegrationTest(MockMvc mvc, JsonMapper json, BingoAdminService admin, BingoService bingo,
                                 BingoCardRepository cards, BingoBoardRepository boards, BingoCardJpaRepository cardEntities,
                                 BingoBoardJpaRepository boardEntities, LeagueService leagues,
                                 LeagueParticipantService participants, RightsService rights, SessionService sessions,
                                 PlatformTransactionManager transactionManager) {
        this.mvc = mvc;
        this.json = json;
        this.admin = admin;
        this.bingo = bingo;
        this.cards = cards;
        this.boards = boards;
        this.cardEntities = cardEntities;
        this.boardEntities = boardEntities;
        this.leagues = leagues;
        this.participants = participants;
        this.rights = rights;
        this.sessions = sessions;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.withZone(ZoneOffset.UTC)).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        administrator = participant();
        cookie = new Cookie(SessionCookie.NAME, sessions.create(administrator.userId()));
        rights.grant(administrator.userId(), Right.EDIT_BINGO);
    }

    @AfterEach
    void cleanUp() {
        boardEntities.deleteAll();
        cardEntities.deleteAll();
    }

    @Test
    void requiresACookieSessionAndTheBingoRightForEveryOperation() throws Exception {
        rights.revoke(administrator.userId(), Right.EDIT_BINGO);
        rights.grant(administrator.userId(), Right.EDIT_CHAMPIONSHIP);
        var id = UUID.randomUUID();
        for (var request : List.of(get(ADMIN), post(ADMIN + "/cards"),
                put(cardUrl(id)), delete(cardUrl(id)), post(ADMIN + "/cards/copy-previous"),
                put(cardUrl(id) + "/progress"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(request.cookie(cookie)).andExpect(status().isForbidden());
        }
        rights.grant(administrator.userId(), Right.EDIT_BINGO);
        mvc.perform(get(ADMIN).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get(ADMIN).header(HttpHeaders.AUTHORIZATION, "Bearer bingo-test-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/internal/v1/bingo/cards/" + id + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bingo-test-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completedRepeats\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsEditsAndDeletesCardsWhileKeepingProgressSeparate() throws Exception {
        var created = mvc.perform(post(ADMIN + "/cards").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(SAVE_CARD))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.season").value(SEASON))
                .andExpect(jsonPath("$.completedRepeats").value(0))
                .andExpect(jsonPath("$.translations.en.description").value("Event")).andReturn();
        var card = json.readValue(created.getResponse().getContentAsByteArray(), BingoAdminCardDto.class);
        mvc.perform(put(cardUrl(card.id()) + "/progress").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completedRepeats\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedRepeats").value(1));
        var edited = """
                {"translations":{"EN":{"description":"Updated","explanation":"Condition"},
                                 "ru":{"description":"Событие","explanation":null},
                                 "de":{"description":"Ereignis","explanation":null}},
                 "probability":"HIGH","requiredRepeats":5}
                """;
        mvc.perform(put(cardUrl(card.id())).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(edited))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(card.id().toString()))
                .andExpect(jsonPath("$.translations.en.description").value("Updated"))
                .andExpect(jsonPath("$.translations.EN").doesNotExist())
                .andExpect(jsonPath("$.translations.de.description").value("Ereignis"))
                .andExpect(jsonPath("$.probability").value("HIGH"))
                .andExpect(jsonPath("$.requiredRepeats").value(5))
                .andExpect(jsonPath("$.completedRepeats").value(1));
        mvc.perform(get(ADMIN).cookie(cookie)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.season").value(SEASON))
                .andExpect(jsonPath("$.structureEditable").value(true))
                .andExpect(jsonPath("$.canCopyPrevious").value(false))
                .andExpect(jsonPath("$.data", hasSize(1)));
        mvc.perform(delete(cardUrl(card.id())).cookie(cookie)).andExpect(status().isNoContent());
        assertThat(cards.findAllBySeason(SEASON)).isEmpty();
        mvc.perform(put(cardUrl(card.id())).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(SAVE_CARD))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(delete(cardUrl(card.id())).cookie(cookie)).andExpect(status().isNotFound());
        mvc.perform(put(cardUrl(card.id()) + "/progress").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completedRepeats\":1}"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"translations\":{},\"probability\":\"LOW\",\"requiredRepeats\":1}",
            "{\"translations\":{\"en\":{\"description\":\" \"}},\"probability\":\"LOW\",\"requiredRepeats\":1}",
            "{\"translations\":{\"en\":{\"description\":\"Event\"}},\"probability\":\"UNKNOWN\",\"requiredRepeats\":1}",
            "{\"translations\":{\"en\":{\"description\":\"Event\"}},\"probability\":\"LOW\",\"requiredRepeats\":0}",
            "{\"translations\":{\"en\":{\"description\":\"Event\"}},\"probability\":\"LOW\",\"requiredRepeats\":-1}",
            "{\"translations\":{\"en\":null},\"probability\":\"LOW\",\"requiredRepeats\":1}",
            "{\"translations\":{\"en_US\":{\"description\":\"Event\"}},\"probability\":\"LOW\",\"requiredRepeats\":1}",
            "{\"translations\":{\"en\":{\"description\":\"Event\"},\"EN\":{\"description\":\"Other\"}},\"probability\":\"LOW\",\"requiredRepeats\":1}"
    })
    void rejectsInvalidCardsWithoutSavingThem(String body) throws Exception {
        mvc.perform(post(ADMIN + "/cards").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(cards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void validatesAbsoluteProgressAndDoesNotOverwriteItWithCardDefinitionUpdates() throws Exception {
        var card = createCards(SEASON, 1, 2).getFirst();
        for (var body : List.of("{}", "{\"completedRepeats\":-1}", "{\"completedRepeats\":4}")) {
            mvc.perform(put(cardUrl(card.id().value()) + "/progress").cookie(cookie)
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(put(cardUrl(card.id().value())).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_CARD.replace("\"requiredRepeats\":3", "\"requiredRepeats\":1")))
                .andExpect(status().isBadRequest());
        assertThat(cards.findById(card.id())).contains(card);
        mvc.perform(put(cardUrl(card.id().value()) + "/progress").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completedRepeats\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
        mvc.perform(put(cardUrl(card.id().value()) + "/progress").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completedRepeats\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void copiesPreviousCardsWithNewIdsAndZeroProgressWithoutChangingTheSource(ApplicationEvents events) throws Exception {
        var previous = createCards(SEASON - 1, 25, 2);
        mvc.perform(get(ADMIN).cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canCopyPrevious").value(true)).andExpect(jsonPath("$.data").isEmpty());
        var copied = mvc.perform(post(ADMIN + "/cards/copy-previous").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value(SEASON))
                .andExpect(jsonPath("$.canCopyPrevious").value(false))
                .andExpect(jsonPath("$.data", hasSize(25))).andReturn();
        var details = json.readValue(copied.getResponse().getContentAsByteArray(), BingoAdminDetailsDto.class);
        assertThat(details.data()).allSatisfy(card -> {
            assertThat(card.completedRepeats()).isZero();
            assertThat(card.requiredRepeats()).isEqualTo(3);
            assertThat(card.translations()).containsOnlyKeys("en", "ru", "de");
            assertThat(card.translations().get("en").explanation()).isEqualTo("Condition");
        });
        assertThat(details.data()).extracting(BingoAdminCardDto::id)
                .doesNotContainAnyElementsOf(previous.stream().map(card -> card.id().value()).toList());
        assertThat(cards.findAllBySeason(SEASON - 1)).containsExactlyInAnyOrderElementsOf(previous);
        mvc.perform(post(ADMIN + "/cards/copy-previous").cookie(cookie)).andExpect(status().isConflict());
        mvc.perform(post(ADMIN + "/cards").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(SAVE_CARD))
                .andExpect(status().isConflict());
        assertThat(cards.findAllBySeason(SEASON)).hasSize(25);
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
        assertThat(events.stream(BingoAchievedEvent.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 24})
    void doesNotCopyAnIncompletePreviousSeason(int count) throws Exception {
        createCards(SEASON - 1, count, 0);
        mvc.perform(get(ADMIN).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.canCopyPrevious").value(false));
        mvc.perform(post(ADMIN + "/cards/copy-previous").cookie(cookie)).andExpect(status().isConflict());
        assertThat(cards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void keepsStructureFixedAfterFirstBoardButAllowsTranslationsProbabilityAndProgress(ApplicationEvents events) throws Exception {
        createCards(SEASON, 25, 0);
        var player = participant();
        var created = bingo.createOwn(player.userId(), player.leagueId(), Optional.empty()).board();
        var order = created.board().cards();
        var cardId = order.getFirst();
        mvc.perform(get(ADMIN).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.structureEditable").value(false));
        mvc.perform(delete(cardUrl(cardId.value())).cookie(cookie)).andExpect(status().isConflict());
        mvc.perform(post(ADMIN + "/cards").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(SAVE_CARD))
                .andExpect(status().isConflict());
        mvc.perform(put(cardUrl(cardId.value())).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_CARD.replace("\"requiredRepeats\":3", "\"requiredRepeats\":4")))
                .andExpect(status().isConflict());
        mvc.perform(put(cardUrl(cardId.value())).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_CARD.replace("\"MEDIUM\"", "\"HIGH\"").replace("\"Event\"", "\"Updated\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.translations.en.description").value("Updated"))
                .andExpect(jsonPath("$.probability").value("HIGH")).andExpect(jsonPath("$.completedRepeats").value(0));

        order.subList(0, 4).forEach(id -> bingo.updateProgress(id, 3));
        var fifth = cardUrl(order.get(4).value()) + "/progress";
        mvc.perform(put(fifth).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"completedRepeats\":3}"))
                .andExpect(status().isOk());
        mvc.perform(put(fifth).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"completedRepeats\":0}"))
                .andExpect(status().isOk());
        mvc.perform(put(fifth).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"completedRepeats\":3}"))
                .andExpect(status().isOk());
        var reread = bingo.getOwn(player.userId(), player.leagueId(), Optional.empty()).board();
        assertThat(reread.board().cards()).containsExactlyElementsOf(order);
        assertThat(reread.board().bingoAchieved()).isTrue();
        assertThat(events.stream(BingoAchievedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void leavesArchivedCardsReadOnly() throws Exception {
        var archived = createCards(SEASON - 1, 1, 1).getFirst();
        mvc.perform(put(cardUrl(archived.id().value())).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(SAVE_CARD))
                .andExpect(status().isConflict());
        mvc.perform(delete(cardUrl(archived.id().value())).cookie(cookie)).andExpect(status().isConflict());
        mvc.perform(put(cardUrl(archived.id().value()) + "/progress").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completedRepeats\":2}"))
                .andExpect(status().isConflict());
        assertThat(cards.findById(archived.id())).contains(archived);
    }

    @Test
    void serializesDefinitionChangesWithFirstBoardCreation() throws Exception {
        var seasonCards = createCards(SEASON, 25, 0);
        var card = seasonCards.getFirst();
        var player = participant();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var started = new CountDownLatch(1);
            var waiting = transactions.execute(status -> {
                bingo.createOwn(player.userId(), player.leagueId(), Optional.empty());
                var future = executor.submit(() -> {
                    started.countDown();
                    return admin.updateCard(card.id(), TRANSLATIONS, BingoCardProbability.MEDIUM, 4);
                });
                await(started);
                assertThat(future.isDone()).isFalse();
                return future;
            });
            assertThatThrownBy(() -> waiting.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BingoConflictException.class);
        }
        assertThat(cards.findById(card.id())).contains(card);
        assertThat(boards.findAllBySeason(SEASON)).hasSize(1);
    }

    @Test
    void serializesConcurrentCopyCommandsWithoutCreatingDuplicates() throws Exception {
        createCards(SEASON - 1, 25, 2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var started = new CountDownLatch(1);
            var waiting = transactions.execute(status -> {
                admin.copyPrevious();
                var future = executor.submit(() -> {
                    started.countDown();
                    return admin.copyPrevious();
                });
                await(started);
                assertThat(future.isDone()).isFalse();
                return future;
            });
            assertThatThrownBy(() -> waiting.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BingoConflictException.class);
        }
        assertThat(cards.findAllBySeason(SEASON)).hasSize(25);
    }

    private List<BingoCard> createCards(int season, int count, int progress) {
        var result = IntStream.range(0, count).mapToObj(i -> new BingoCard(new BingoCardId(UUID.randomUUID()),
                season, TRANSLATIONS, BingoCardProbability.MEDIUM, 3, progress)).toList();
        transactions.executeWithoutResult(status -> result.forEach(cards::create));
        return result;
    }

    private LeagueParticipant participant() {
        var league = leagues.createManual("Bingo admin test league");
        var identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        return participants.ensureParticipant(league.id(), identity, "Participant");
    }

    private String cardUrl(UUID id) {
        return ADMIN + "/cards/" + id;
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
