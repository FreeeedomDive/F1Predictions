package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoBoardRepository;
import com.xdd.f1.application.bingo.BingoCardRepository;
import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.bootstrap.auth.SessionCookie;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.contract.bingo.BingoCardDto;
import com.xdd.f1.contract.bingo.BingoDetailsDto;
import com.xdd.f1.domain.bingo.BingoBoard;
import com.xdd.f1.domain.bingo.BingoBoardId;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.bingo.BingoCardProbability;
import com.xdd.f1.domain.bingo.BingoCardTranslation;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "internal-api.service-key=bingo-test-key")
class BingoApiIntegrationTest extends AbstractIntegrationTest {
    private static final int SEASON = 2027;
    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");
    private static final String BEARER = "Bearer bingo-test-key";
    private static final String PARTICIPANT_HEADER = "F1P-ParticipantId";
    private static final Map<String, BingoCardTranslation> TRANSLATIONS = Map.of(
            "en", new BingoCardTranslation("Event", "English condition"),
            "ru", new BingoCardTranslation("Событие", null));
    private final MockMvc mvc;
    private final JsonMapper json;
    private final BingoCardRepository cards;
    private final BingoBoardRepository boards;
    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final SessionService sessions;
    private final BingoCardJpaRepository cardEntities;
    private final BingoBoardJpaRepository boardEntities;
    private final TransactionTemplate transactions;

    @MockitoBean
    private Clock clock;

    private LeagueParticipant alice;
    private Cookie cookie;

    @Autowired
    BingoApiIntegrationTest(MockMvc mvc, JsonMapper json, BingoCardRepository cards, BingoBoardRepository boards,
                            LeagueRepository leagues, LeagueParticipantService participants, SessionService sessions,
                            BingoCardJpaRepository cardEntities, BingoBoardJpaRepository boardEntities,
                            PlatformTransactionManager transactionManager) {
        this.mvc = mvc;
        this.json = json;
        this.cards = cards;
        this.boards = boards;
        this.leagues = leagues;
        this.participants = participants;
        this.sessions = sessions;
        this.cardEntities = cardEntities;
        this.boardEntities = boardEntities;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.withZone(ZoneOffset.UTC)).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        alice = participant(SEASON - 1);
        cookie = cookie(alice);
    }

    @AfterEach
    void cleanUp() {
        boardEntities.deleteAll();
        cardEntities.deleteAll();
    }

    @Test
    void requiresTheCorrectAuthenticationAndExistingParticipation() throws Exception {
        var outsider = participant(SEASON);
        var nativeUrl = nativeUrl(alice.leagueId());
        var internalUrl = internalUrl(alice.leagueId());

        mvc.perform(get(nativeUrl).header(PARTICIPANT_HEADER, alice.id().value())).andExpect(status().isUnauthorized());
        mvc.perform(put(nativeUrl).header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isUnauthorized());
        mvc.perform(get(nativeUrl).cookie(new Cookie(SessionCookie.NAME, "invalid"))).andExpect(status().isUnauthorized());
        mvc.perform(get(nativeUrl).cookie(cookie(outsider))).andExpect(status().isNotFound());
        mvc.perform(put(nativeUrl).cookie(cookie(outsider))).andExpect(status().isNotFound());
        mvc.perform(get(internalUrl).cookie(cookie).header(PARTICIPANT_HEADER, alice.id().value()))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(internalUrl).header(HttpHeaders.AUTHORIZATION, "Bearer wrong")
                        .header(PARTICIPANT_HEADER, alice.id().value())).andExpect(status().isUnauthorized());
        mvc.perform(get(internalUrl).header(HttpHeaders.AUTHORIZATION, BEARER)).andExpect(status().isBadRequest());
        mvc.perform(put(internalUrl).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get(internalUrl).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, outsider.id().value())).andExpect(status().isNotFound());
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void sharesOneOrderedBoardAcrossLanguagesAndApisWithoutTrustingOwnerOverrides() throws Exception {
        var seasonCards = createCards(SEASON, 25, TRANSLATIONS);
        var bob = participants.ensureParticipant(alice.leagueId(), identity(), "Bob");
        var nativeUrl = nativeUrl(alice.leagueId());
        var internalUrl = internalUrl(alice.leagueId());

        mvc.perform(get(nativeUrl).cookie(cookie)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.season").value(SEASON))
                .andExpect(jsonPath("$.activeSeason").value(SEASON))
                .andExpect(jsonPath("$.canCreate").value(true))
                .andExpect(jsonPath("$.board").value(nullValue()));
        mvc.perform(get(internalUrl).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, alice.id().value()).param("lang", "ru"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canCreate").value(true));
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();

        var result = mvc.perform(put(nativeUrl).cookie(cookie)
                        .header(PARTICIPANT_HEADER, bob.id().value()).param("participantId", bob.id().value().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + bob.userId().value() + "\",\"participantId\":\"" + bob.id().value() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.canCreate").value(false))
                .andExpect(jsonPath("$.board.participantId").value(alice.id().value().toString()))
                .andExpect(jsonPath("$.board.completedLines").doesNotExist())
                .andExpect(jsonPath("$.board.cards", hasSize(25)))
                .andExpect(jsonPath("$.board.cards[0].language").value("en"))
                .andExpect(jsonPath("$.board.cards[0].description").value("Event"))
                .andExpect(jsonPath("$.board.cards[0].explanation").value("English condition"))
                .andExpect(jsonPath("$.board.cards[0].probability").value("MEDIUM"))
                .andExpect(jsonPath("$.board.cards[0].requiredRepeats").value(3))
                .andExpect(jsonPath("$.board.cards[0].completedRepeats").value(1))
                .andExpect(jsonPath("$.board.cards[0].completed").value(false))
                .andExpect(jsonPath("$.board.cards[0].translations").doesNotExist()).andReturn();
        var english = json.readValue(result.getResponse().getContentAsByteArray(), BingoDetailsDto.class);
        assertThat(english.board().cards()).extracting(BingoCardDto::id)
                .containsExactlyInAnyOrderElementsOf(seasonCards.stream().map(card -> card.id().value()).toList());

        var translated = mvc.perform(put(internalUrl).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, alice.id().value()).param("lang", "RU"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.board.id").value(english.board().id().toString()))
                .andExpect(jsonPath("$.board.cards[0].language").value("ru"))
                .andExpect(jsonPath("$.board.cards[0].description").value("Событие"))
                .andExpect(jsonPath("$.board.cards[0].explanation").value(nullValue())).andReturn();
        var russian = json.readValue(translated.getResponse().getContentAsByteArray(), BingoDetailsDto.class);
        assertThat(russian.board().cards()).extracting(BingoCardDto::id)
                .containsExactlyElementsOf(english.board().cards().stream().map(BingoCardDto::id).toList());
        var reread = mvc.perform(get(nativeUrl).cookie(cookie)).andExpect(status().isOk()).andReturn();
        assertThat(json.readValue(reread.getResponse().getContentAsByteArray(), BingoDetailsDto.class)).isEqualTo(english);
        mvc.perform(get(internalUrl).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, bob.id().value()).param("userId", alice.userId().value().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.board").value(nullValue()));
        assertThat(boards.findAllBySeason(SEASON)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "fr", "en-US"})
    void fallsBackToTheWholeEnglishTranslationAndReportsItsLanguage(String language) throws Exception {
        createCards(SEASON, 25, Map.of("en", TRANSLATIONS.get("en"),
                "ru", new BingoCardTranslation(" ", "Do not mix this explanation with English")));

        mvc.perform(put(nativeUrl(alice.leagueId())).cookie(cookie).param("lang", language))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.board.cards[0].language").value("en"))
                .andExpect(jsonPath("$.board.cards[0].description").value("Event"))
                .andExpect(jsonPath("$.board.cards[0].explanation").value("English condition"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 24, 26})
    void reportsUnavailableCardsAndRejectsCreationWithoutSavingABoard(int count) throws Exception {
        createCards(SEASON, count, TRANSLATIONS);
        mvc.perform(get(nativeUrl(alice.leagueId())).cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canCreate").value(false)).andExpect(jsonPath("$.board").value(nullValue()));
        mvc.perform(put(nativeUrl(alice.leagueId())).cookie(cookie)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        mvc.perform(put(internalUrl(alice.leagueId())).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, alice.id().value()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void readsSavedArchivesWithoutCreatingMissingArchivedOrFutureBoards() throws Exception {
        var archivedCards = createCards(SEASON - 1, 25, TRANSLATIONS);
        var order = archivedCards.stream().map(BingoCard::id).toList().reversed();
        var archived = new BingoBoard(new BingoBoardId(UUID.randomUUID()), alice.leagueId(), SEASON - 1, alice.id(), order, true);
        transactions.executeWithoutResult(status -> boards.create(archived));
        var bob = participants.ensureParticipant(alice.leagueId(), identity(), "Bob");

        var result = mvc.perform(get(nativeUrl(alice.leagueId())).cookie(cookie).param("season", String.valueOf(SEASON - 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeSeason").value(SEASON))
                .andExpect(jsonPath("$.canCreate").value(false)).andExpect(jsonPath("$.board.bingoAchieved").value(true))
                .andExpect(jsonPath("$.board.completedLines").doesNotExist()).andReturn();
        var details = json.readValue(result.getResponse().getContentAsByteArray(), BingoDetailsDto.class);
        assertThat(details.board().cards()).extracting(BingoCardDto::id)
                .containsExactlyElementsOf(order.stream().map(BingoCardId::value).toList());
        mvc.perform(put(internalUrl(alice.leagueId())).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, bob.id().value()).param("season", String.valueOf(SEASON - 1)))
                .andExpect(status().isConflict());
        mvc.perform(get(nativeUrl(alice.leagueId())).cookie(cookie).param("season", String.valueOf(SEASON + 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canCreate").value(false))
                .andExpect(jsonPath("$.board").value(nullValue()));
        mvc.perform(put(nativeUrl(alice.leagueId())).cookie(cookie).param("season", String.valueOf(SEASON + 1)))
                .andExpect(status().isConflict());
        assertThat(boards.findAllBySeason(SEASON - 1)).containsExactly(archived);
        assertThat(boards.findAllBySeason(SEASON + 1)).isEmpty();
    }

    @Test
    void keepsSeasonsBeforeTheLeagueStartsUnavailable() throws Exception {
        createCards(SEASON, 25, TRANSLATIONS);
        var future = participant(SEASON + 1);
        mvc.perform(get(nativeUrl(future.leagueId())).cookie(cookie(future))).andExpect(status().isOk())
                .andExpect(jsonPath("$.canCreate").value(false)).andExpect(jsonPath("$.board").value(nullValue()));
        mvc.perform(put(nativeUrl(future.leagueId())).cookie(cookie(future))).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number"})
    void rejectsInvalidSeasonsBeforeCreatingABoard(String season) throws Exception {
        createCards(SEASON, 25, TRANSLATIONS);
        mvc.perform(get(nativeUrl(alice.leagueId())).cookie(cookie).param("season", season)).andExpect(status().isBadRequest());
        mvc.perform(put(nativeUrl(alice.leagueId())).cookie(cookie).param("season", season)).andExpect(status().isBadRequest());
        mvc.perform(put(internalUrl(alice.leagueId())).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, alice.id().value()).param("season", season))
                .andExpect(status().isBadRequest());
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_US", "und", "not a language"})
    void rejectsInvalidLanguagesBeforeCreatingABoard(String language) throws Exception {
        createCards(SEASON, 25, TRANSLATIONS);
        mvc.perform(get(nativeUrl(alice.leagueId())).cookie(cookie).param("lang", language)).andExpect(status().isBadRequest());
        mvc.perform(put(nativeUrl(alice.leagueId())).cookie(cookie).param("lang", language)).andExpect(status().isBadRequest());
        mvc.perform(put(internalUrl(alice.leagueId())).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, alice.id().value()).param("lang", language))
                .andExpect(status().isBadRequest());
        assertThat(boards.findAllBySeason(SEASON)).isEmpty();
    }

    @Test
    void doesNotExposeRoutesForReadingOrCreatingAnotherParticipantsBoard() throws Exception {
        var foreignId = UUID.randomUUID();
        var nativeUrl = nativeUrl(alice.leagueId()).replace("/me", "/" + foreignId);
        var internalUrl = internalUrl(alice.leagueId()).replace("/me", "/" + foreignId);

        mvc.perform(get(nativeUrl).cookie(cookie)).andExpect(status().isNotFound());
        mvc.perform(put(nativeUrl).cookie(cookie)).andExpect(status().isNotFound());
        mvc.perform(get(internalUrl).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, alice.id().value())).andExpect(status().isNotFound());
        mvc.perform(put(internalUrl).header(HttpHeaders.AUTHORIZATION, BEARER)
                        .header(PARTICIPANT_HEADER, alice.id().value())).andExpect(status().isNotFound());
    }

    private List<BingoCard> createCards(int season, int count, Map<String, BingoCardTranslation> translations) {
        var result = IntStream.range(0, count).mapToObj(i -> new BingoCard(new BingoCardId(UUID.randomUUID()),
                season, translations, BingoCardProbability.MEDIUM, 3, 1)).toList();
        transactions.executeWithoutResult(status -> result.forEach(cards::create));
        return result;
    }

    private LeagueParticipant participant(int firstSeason) {
        var league = new League(new LeagueId(UUID.randomUUID()), "Bingo API league", new LeagueSource.Manual(),
                new LeagueSettings(null, false, firstSeason), NOW);
        transactions.executeWithoutResult(status -> leagues.create(league));
        return participants.ensureParticipant(league.id(), identity(), "Participant");
    }

    private UserIdentityId identity() {
        return new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
    }

    private Cookie cookie(LeagueParticipant participant) {
        return new Cookie(SessionCookie.NAME, sessions.create(participant.userId()));
    }

    private String nativeUrl(LeagueId leagueId) {
        return "/api/v1/leagues/" + leagueId.value() + "/bingo/boards/me";
    }

    private String internalUrl(LeagueId leagueId) {
        return "/internal/v1/leagues/" + leagueId.value() + "/bingo/boards/me";
    }
}
