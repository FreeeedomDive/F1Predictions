package com.xdd.f1.bootstrap.championship;

import com.xdd.f1.application.championship.ChampionshipStandingsSnapshot;
import com.xdd.f1.infrastructure.http.jolpica.JolpicaChampionshipStandingsProvider;
import com.xdd.f1.infrastructure.http.jolpica.JolpicaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JolpicaChampionshipStandingsProviderTest {
    private static final int SEASON = 2026;
    private static final String BASE_URL = "https://jolpica.test/ergast/f1";
    private static final String URL = BASE_URL + "/2026/driverstandings/?limit=100";
    private static final String STANDINGS = """
            {
              "MRData": {
                "limit":"100", "offset":"0", "total":"11",
                "StandingsTable": {
                  "season":"2026", "round":"20",
                  "StandingsLists":[{"season":"2026", "round":"20", "DriverStandings":[
                    {"position":"10","Driver":{"driverId":"rafael_camara","familyName":"Câmara"}},
                    {"position":"2","Driver":{"driverId":"perez","familyName":"Pérez"}},
                    {"position":"11","Driver":{"driverId":"arvid_lindblad"}},
                    {"position":"1","Driver":{"driverId":"max_verstappen"}},
                    {"position":"3","Driver":{"driverId":"hulkenberg","familyName":"Hülkenberg"}},
                    {"position":"4","Driver":{"driverId":"norris"}},
                    {"position":"5","Driver":{"driverId":"piastri"}},
                    {"position":"6","Driver":{"driverId":"sainz"}},
                    {"position":"7","Driver":{"driverId":"hamilton"}},
                    {"position":"8","Driver":{"driverId":"albon"}},
                    {"position":"9","Driver":{"driverId":"russell"}}
                  ]}]
                }
              }
            }
            """;

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final JolpicaChampionshipStandingsProvider provider = new JolpicaChampionshipStandingsProvider(
            HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build())).build().createClient(JolpicaClient.class));

    @Test
    void loadsCompleteStandingsInNumericOrder() {
        server.expect(requestTo(URL)).andRespond(withSuccess(STANDINGS, MediaType.APPLICATION_JSON));

        assertThat(provider.loadStandings(SEASON)).contains(new ChampionshipStandingsSnapshot(20, List.of(
                "Verstappen", "Perez", "Hulkenberg", "Norris", "Piastri", "Sainz", "Hamilton", "Albon", "Russell", "Camara", "Lindblad")));
        server.verify();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\"offset\":\"0\"|\"offset\":\"1\"",
            "\"limit\":\"100\"|\"limit\":\"10\"",
            "\"total\":\"11\"|\"total\":\"12\"",
            "\"season\":\"2026\"|\"season\":\"2025\"",
            "StandingsLists\":[{\"season\":\"2026\", \"round\":\"20\"|StandingsLists\":[{\"season\":\"2026\", \"round\":\"19\"",
            "\"position\":\"2\"|\"position\":\"1\"",
            "\"position\":\"3\"|\"position\":\"12\"",
            "arvid_lindblad|other_verstappen"
    })
    void rejectsPartialOrInvalidSnapshots(String from, String to) {
        server.expect(requestTo(URL)).andRespond(withSuccess(STANDINGS.replace(from, to), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.loadStandings(SEASON)).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void returnsNoSnapshotBeforeTheFirstRound() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"MRData":{"limit":"100","offset":"0","total":"0","StandingsTable":{"season":"2026","StandingsLists":[]}}}
                """, MediaType.APPLICATION_JSON));
        assertThat(provider.loadStandings(SEASON)).isEmpty();
        server.verify();
    }

    @Test
    void propagatesRateLimitFailure() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> provider.loadStandings(SEASON)).isInstanceOf(HttpClientErrorException.TooManyRequests.class);
        server.verify();
    }
}
