package com.xdd.f1.infrastructure.http.jolpica;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record JolpicaDriverStandingsResponse(@JsonProperty("MRData") MrData mrData) {
    public record MrData(
            String limit,
            String offset,
            String total,
            @JsonProperty("StandingsTable") StandingsTable standingsTable
    ) {
    }

    public record StandingsTable(String season, String round, @JsonProperty("StandingsLists") List<StandingsList> standingsLists) {
    }

    public record StandingsList(String season, String round, @JsonProperty("DriverStandings") List<DriverStanding> driverStandings) {
    }

    public record DriverStanding(String position, @JsonProperty("Driver") Driver driver) {
    }

    public record Driver(String driverId) {
    }
}
