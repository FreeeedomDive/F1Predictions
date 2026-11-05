package com.xdd.f1.infrastructure.persistence.race.jolpica;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record JolpicaRaceTable(
        @JsonProperty("Races") List<JolpicaRace> races
) {
}
