package com.xdd.f1.infrastructure.http.jolpica;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record JolpicaStandingsTable(
        String season,
        String round,
        @JsonProperty("StandingsLists") List<JolpicaStandingsList> standingsLists
) {
}
