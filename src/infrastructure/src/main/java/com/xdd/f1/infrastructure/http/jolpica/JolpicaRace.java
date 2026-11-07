package com.xdd.f1.infrastructure.http.jolpica;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JolpicaRace(
        String season,
        String round,
        String raceName,
        String date,
        String time,
        @JsonProperty("Qualifying") JolpicaSession qualifying,
        @JsonProperty("SprintQualifying") JolpicaSession sprintQualifying,
        @JsonProperty("Sprint") JolpicaSession sprint
) {
}
