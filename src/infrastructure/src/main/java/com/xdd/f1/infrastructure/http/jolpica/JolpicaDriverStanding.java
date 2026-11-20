package com.xdd.f1.infrastructure.http.jolpica;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JolpicaDriverStanding(String position, @JsonProperty("Driver") JolpicaDriver driver) {
}
