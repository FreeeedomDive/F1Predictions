package com.xdd.f1.bootstrap.race;

import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceId;
import com.xdd.f1.domain.race.RaceStatus;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.infrastructure.http.openf1.OpenF1Client;
import com.xdd.f1.infrastructure.http.openf1.OpenF1StartingGridProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenF1StartingGridProviderTest {
    private static final String BASE_URL = "https://api.openf1.org/v1";
    private static final Instant NOW = Instant.parse("2026-11-07T19:30:00Z");
    private static final String SESSIONS = """
            [
              {"session_key":101,"meeting_key":1,"session_name":"Race","date_start":"2026-03-08T17:00:00Z"},
              {"session_key":999,"meeting_key":99,"session_name":"Race","date_start":"2026-11-08T17:00:00Z","is_cancelled":true},
              {"session_key":11412,"meeting_key":1299,"session_name":"Race","date_start":"2026-11-08T17:00:00Z","year":2026},
              {"session_key":11407,"meeting_key":1299,"session_name":"Qualifying","date_start":"2026-11-07T18:00:00Z","is_cancelled":true},
              {"session_key":11408,"meeting_key":1299,"session_name":"Qualifying","date_start":"2026-11-07T18:00:00Z","date_end":"2026-11-07T19:00:00Z"},
              {"session_key":444,"meeting_key":1300,"session_name":"Qualifying","date_start":"2026-11-07T18:00:00Z","date_end":"2026-11-07T19:00:00Z"},
              {"session_key":11409,"meeting_key":1299,"session_name":"Practice 3","date_start":"2026-11-07T14:00:00Z"},
              {"session_key":11405,"meeting_key":1299,"session_name":"Sprint","date_start":"2026-11-07T14:00:00Z"},
              {"session_key":11406,"meeting_key":1299,"session_name":"Sprint Qualifying","date_start":"2026-11-06T18:00:00Z","date_end":"2026-11-06T19:00:00Z"}
            ]
            """;
    private static final String GRID = """
            [{"driver_number":51,"position":2,"session_key":11408},
             {"driver_number":21,"position":3},
             {"driver_number":27,"position":1}]
            """;
    private static final String DRIVERS = """
            [{"driver_number":21,"broadcast_name":"N DE VRIES"},
             {"driver_number":27,"broadcast_name":"N HULKENBERG","last_name":"Hülkenberg"},
             {"driver_number":51,"broadcast_name":"R CAMARA","last_name":"Câmara","full_name":"Rafael CAMARA"}]
            """;

    private MockRestServiceServer server;
    private OpenF1Client client;
    private OpenF1StartingGridProvider provider;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
                .build().createClient(OpenF1Client.class);
        provider = new OpenF1StartingGridProvider(client, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void loadsQualifyingGridByDatesAndMeetingAndFormatsBroadcastNamesInPositionOrder() {
        expectSessions(SESSIONS);
        expectGrid(11408, GRID);
        expectDrivers(11408, DRIVERS);

        assertThat(provider.loadStartingGrid(race(RaceType.RACE))).extracting(Driver::name)
                .containsExactly("Hulkenberg", "Camara", "De vries");
    }

    @Test
    void loadsSprintGridFromSprintQualifyingOfTheSameMeeting() {
        expectSessions(SESSIONS);
        expectGrid(11406, "[{\"driver_number\":51,\"position\":1}]");
        expectDrivers(11406, "[{\"driver_number\":51,\"broadcast_name\":\"R CAMARA\"}]");

        assertThat(provider.loadStartingGrid(race(RaceType.SPRINT))).containsExactly(new Driver("Camara"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            """
            [{"session_key":11412,"meeting_key":1299,"session_name":"Race","date_start":"2026-11-08T17:00:00Z"},
             {"session_key":11408,"meeting_key":1300,"session_name":"Qualifying","date_start":"2026-11-07T18:00:00Z","date_end":"2026-11-07T19:00:00Z"}]
            """,
            """
            [{"session_key":11412,"meeting_key":1299,"session_name":"Race","date_start":"2026-11-08T17:00:00Z"},
             {"session_key":11413,"meeting_key":1300,"session_name":"Race","date_start":"2026-11-08T18:00:00Z"},
             {"session_key":11408,"meeting_key":1299,"session_name":"Qualifying","date_start":"2026-11-07T18:00:00Z","date_end":"2026-11-07T19:00:00Z"}]
            """,
            """
            [{"session_key":11412,"meeting_key":1299,"session_name":"Race","date_start":"2026-11-08T17:00:00Z"},
             {"session_key":11408,"meeting_key":1299,"session_name":"Qualifying","date_start":"2026-11-05T18:00:00Z","date_end":"2026-11-05T19:00:00Z"}]
            """
    })
    void waitsWhenNoUniqueSessionMatchesTheCalendar(String sessions) {
        expectSessions(sessions);

        assertThat(provider.loadStartingGrid(race(RaceType.RACE))).isEmpty();
    }

    @Test
    void waitsUntilThirtyMinutesAfterQualifyingEnds() {
        expectSessions(SESSIONS);
        provider = new OpenF1StartingGridProvider(client, Clock.fixed(NOW.minusSeconds(1), ZoneOffset.UTC));

        assertThat(provider.loadStartingGrid(race(RaceType.RACE))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitsWhenTheGridIsNotPublishedWithoutRequestingDrivers(boolean notFound) {
        expectSessions(SESSIONS);
        server.expect(requestTo(BASE_URL + "/starting_grid?session_key=11408"))
                .andRespond(notFound ? withStatus(HttpStatus.NOT_FOUND) : withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(provider.loadStartingGrid(race(RaceType.RACE))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"driver_number\":51,\"position\":null}]",
            "[{\"driver_number\":51,\"position\":0}]",
            "[{\"driver_number\":51,\"position\":1},{\"driver_number\":27,\"position\":1}]",
            "[{\"driver_number\":51,\"position\":1},{\"driver_number\":27,\"position\":3}]"
    })
    void rejectsUnspecifiedDuplicateOrNonconsecutivePositions(String grid) {
        expectSessions(SESSIONS);
        expectGrid(11408, grid);
        expectDrivers(11408, DRIVERS);

        assertThat(provider.loadStartingGrid(race(RaceType.RACE))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[{\"driver_number\":51,\"last_name\":\"Câmara\"}]"})
    void rejectsMissingDriverDataWithoutFallingBackToLastName(String drivers) {
        expectSessions(SESSIONS);
        expectGrid(11408, "[{\"driver_number\":51,\"position\":1}]");
        expectDrivers(11408, drivers);

        assertThat(provider.loadStartingGrid(race(RaceType.RACE))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void propagatesSourceFailuresForThePollingCaller(int status) {
        expectSessions(SESSIONS);
        server.expect(requestTo(BASE_URL + "/starting_grid?session_key=11408"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> provider.loadStartingGrid(race(RaceType.RACE)))
                .isInstanceOfSatisfying(RestClientResponseException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(status));
    }

    private void expectSessions(String json) {
        server.expect(requestTo(BASE_URL + "/sessions?year=2026")).andExpect(method(GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectGrid(int sessionKey, String json) {
        server.expect(requestTo(BASE_URL + "/starting_grid?session_key=" + sessionKey)).andExpect(method(GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectDrivers(int sessionKey, String json) {
        server.expect(requestTo(BASE_URL + "/drivers?session_key=" + sessionKey)).andExpect(method(GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private Race race(RaceType type) {
        return new Race(new RaceId(UUID.randomUUID()), 2026, 20, type, "Brazilian Grand Prix", RaceStatus.OPEN,
                Instant.parse(type == RaceType.SPRINT ? "2026-11-06T18:00:00Z" : "2026-11-07T18:00:00Z"),
                Instant.parse(type == RaceType.SPRINT ? "2026-11-07T14:00:00Z" : "2026-11-08T17:00:00Z"));
    }
}
