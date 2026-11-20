package com.xdd.f1.infrastructure.http.jolpica;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JolpicaDriverStandingsResponse(@JsonProperty("MRData") JolpicaDriverStandingsData mrData) {
}
