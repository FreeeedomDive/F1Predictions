package com.xdd.f1.infrastructure.http.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Session(
        @JsonProperty("session_key") int sessionKey,
        @JsonProperty("meeting_key") int meetingKey,
        @JsonProperty("session_name") String sessionName,
        @JsonProperty("date_start") Instant dateStart,
        @JsonProperty("date_end") Instant dateEnd,
        @JsonProperty("is_cancelled") Boolean cancelled
) {
}
