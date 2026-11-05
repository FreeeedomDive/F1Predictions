package com.xdd.f1.bootstrap.race;

import com.xdd.f1.application.race.RaceCalendarEntry;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.infrastructure.http.jolpica.JolpicaClient;
import com.xdd.f1.infrastructure.http.jolpica.JolpicaRaceCalendarProvider;
import com.xdd.f1.infrastructure.http.jolpica.JolpicaSeasonResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JolpicaRaceCalendarProviderTest {

    @Test
    void mapsJolpicaResponseToRaceAndSprintEntries() throws Exception {
        var response = JsonMapper.builder().build().readValue("""
                {
                  "MRData": {
                    "RaceTable": {
                      "Races": [
                        {
                          "season": "2027",
                          "round": "5",
                          "raceName": "Miami Grand Prix",
                          "date": "2027-05-02",
                          "time": "18:00:00Z",
                          "Qualifying": {
                            "date": "2027-05-01",
                            "time": "18:00:00Z"
                          },
                          "SprintQualifying": {
                            "date": "2027-04-30",
                            "time": "15:30:00Z"
                          },
                          "Sprint": {
                            "date": "2027-05-01",
                            "time": "14:00:00Z"
                          }
                        }
                      ]
                    }
                  }
                }
                """, JolpicaSeasonResponse.class);
        var client = mock(JolpicaClient.class);
        when(client.getSeason(2027)).thenReturn(response);
        var provider = new JolpicaRaceCalendarProvider(client);

        var entries = provider.loadSeason(2027);

        assertThat(entries).containsExactly(
                new RaceCalendarEntry(
                        2027,
                        5,
                        RaceType.RACE,
                        "Miami Grand Prix",
                        Instant.parse("2027-05-01T18:00:00Z"),
                        Instant.parse("2027-05-02T18:00:00Z")
                ),
                new RaceCalendarEntry(
                        2027,
                        5,
                        RaceType.SPRINT,
                        "Miami Grand Prix",
                        Instant.parse("2027-04-30T15:30:00Z"),
                        Instant.parse("2027-05-01T14:00:00Z")
                )
        );
    }
}
