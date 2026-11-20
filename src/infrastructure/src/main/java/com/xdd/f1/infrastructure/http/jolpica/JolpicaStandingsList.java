package com.xdd.f1.infrastructure.http.jolpica;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record JolpicaStandingsList(
        String season,
        String round,
        @JsonProperty("DriverStandings") List<JolpicaDriverStanding> driverStandings
) {
}
