package com.xdd.f1.domain.league;

import java.util.Objects;
import java.util.UUID;

public record LeagueParticipantId(UUID value) {

    public LeagueParticipantId {
        Objects.requireNonNull(value, "League participant ID must not be null");
    }
}
