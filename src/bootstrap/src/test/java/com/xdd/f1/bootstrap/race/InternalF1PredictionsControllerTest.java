package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.application.prediction.PredictionService;
import com.xdd.f1.application.prediction.PredictionsClosedException;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.lineup.RaceLineupService;
import com.xdd.f1.application.race.result.RaceResultService;
import com.xdd.f1.application.standings.SeasonStandings;
import com.xdd.f1.application.standings.SeasonStandingsService;
import com.xdd.f1.application.statistics.SeasonStatsService;
import com.xdd.f1.bootstrap.auth.InternalApiProperties;
import com.xdd.f1.bootstrap.auth.InternalAuthenticationConfiguration;
import com.xdd.f1.bootstrap.auth.InternalAuthenticationInterceptor;
import com.xdd.f1.bootstrap.error.ApiExceptionHandler;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalF1PredictionsControllerTest {
    private static final String SERVICE_KEY = "test-internal-service-key";

    private final RaceService races = mock(RaceService.class);
    private final PredictionService predictions = mock(PredictionService.class);
    private final RaceResultService results = mock(RaceResultService.class);
    private final SeasonStandingsService standings = mock(SeasonStandingsService.class);
    private final SeasonStatsService stats = mock(SeasonStatsService.class);
    private final RaceLineupService lineups = mock(RaceLineupService.class);
    private final LeagueParticipantRepository participants = mock(LeagueParticipantRepository.class);
    private final LeagueId leagueId = new LeagueId(UUID.randomUUID());
    private final LeagueParticipantId participantId = new LeagueParticipantId(UUID.randomUUID());
    private final UserId userId = new UserId(UUID.randomUUID());
    private final RaceId raceId = new RaceId(UUID.randomUUID());

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var authentication = new InternalAuthenticationInterceptor(new InternalApiProperties(SERVICE_KEY));
        var configuration = new InternalAuthenticationConfiguration(authentication, participants);
        var resolvers = new ArrayList<HandlerMethodArgumentResolver>();
        configuration.addArgumentResolvers(resolvers);
        mvc = MockMvcBuilders.standaloneSetup(new InternalF1PredictionsController(
                        races, predictions, results, standings, stats, lineups, Clock.systemUTC()))
                .setCustomArgumentResolvers(resolvers.toArray(HandlerMethodArgumentResolver[]::new))
                .addInterceptors(authentication)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void resolvesParticipantInRequestedLeagueAndUsesItsUserForOwnRead() throws Exception {
        when(participants.findByLeagueAndId(leagueId, participantId)).thenReturn(Optional.of(participant()));
        when(predictions.findOwn(userId, leagueId, raceId)).thenReturn(Optional.empty());

        mvc.perform(get(predictionUrl())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_KEY)
                        .header("F1P-ParticipantId", participantId.value()))
                .andExpect(status().isNoContent());

        verify(predictions).findOwn(userId, leagueId, raceId);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "not-a-uuid"})
    void rejectsMissingOrMalformedParticipantHeader(String header) throws Exception {
        var request = get(predictionUrl()).header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_KEY);
        if (header != null) request.header("F1P-ParticipantId", header);

        mvc.perform(request).andExpect(status().isBadRequest());

        verifyNoInteractions(participants, predictions);
    }

    @Test
    void rejectsParticipantFromAnotherLeague() throws Exception {
        var otherLeague = new LeagueId(UUID.randomUUID());
        when(participants.findByLeagueAndId(otherLeague, participantId)).thenReturn(Optional.empty());

        mvc.perform(get("/internal/v1/leagues/{leagueId}/races/{raceId}/prediction", otherLeague.value(), raceId.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_KEY)
                        .header("F1P-ParticipantId", participantId.value()))
                .andExpect(status().isNotFound());

        verify(participants).findByLeagueAndId(otherLeague, participantId);
        verifyNoInteractions(predictions);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Bearer incorrect-key", "test-internal-service-key"})
    void rejectsInvalidServiceAuthorizationBeforeResolvingParticipant(String authorization) throws Exception {
        var request = get(predictionUrl()).header("F1P-ParticipantId", participantId.value());
        if (authorization != null) request.header(HttpHeaders.AUTHORIZATION, authorization);

        mvc.perform(request).andExpect(status().isUnauthorized());

        verifyNoInteractions(participants, predictions);
    }

    @Test
    void standingsDoesNotRequireParticipantHeader() throws Exception {
        when(standings.getStandings(leagueId, 2026)).thenReturn(SeasonStandings.empty(2026));

        mvc.perform(get("/internal/v1/leagues/{leagueId}/standings", leagueId.value())
                        .param("season", "2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value(2026))
                .andExpect(jsonPath("$.rows").isEmpty());

        verifyNoInteractions(participants);
    }

    @Test
    void savesAsResolvedParticipantAndPreservesClosedPredictionsConflict() throws Exception {
        when(participants.findByLeagueAndId(leagueId, participantId)).thenReturn(Optional.of(participant()));
        when(predictions.save(eq(userId), eq(leagueId), eq(raceId), any()))
                .thenThrow(new PredictionsClosedException(raceId));

        mvc.perform(put(predictionUrl())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + SERVICE_KEY)
                        .header("F1P-ParticipantId", participantId.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenthPlacePickedDriver":"Piastri","dnfPickedDrivers":[],
                                 "safetyCarsPrediction":"ZERO","firstPlaceLeadPrediction":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Predictions are not open for race with id: " + raceId.value()));
    }

    private LeagueParticipant participant() {
        return new LeagueParticipant(participantId, leagueId, userId, "Participant", Instant.EPOCH);
    }

    private String predictionUrl() {
        return "/internal/v1/leagues/" + leagueId.value() + "/races/" + raceId.value() + "/prediction";
    }
}
