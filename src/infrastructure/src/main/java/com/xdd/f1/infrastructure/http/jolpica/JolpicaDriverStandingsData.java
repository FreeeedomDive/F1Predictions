package com.xdd.f1.infrastructure.http.jolpica;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JolpicaDriverStandingsData(
        String limit,
        String offset,
        String total,
        @JsonProperty("StandingsTable") JolpicaStandingsTable standingsTable
) {
}
