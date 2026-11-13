package com.xdd.f1.infrastructure.http.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1GridEntry(@JsonProperty("driver_number") int driverNumber, Integer position) {
}
