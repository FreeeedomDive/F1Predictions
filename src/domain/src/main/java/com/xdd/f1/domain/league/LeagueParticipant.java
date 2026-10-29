package com.xdd.f1.domain.league;

import com.xdd.f1.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;

public record LeagueParticipant(
        LeagueParticipantId id,
        LeagueId leagueId,
        UserId userId,
        String displayName,
        Instant joinedAt
) {

    public LeagueParticipant {
        Objects.requireNonNull(id, "League participant ID must not be null");
        Objects.requireNonNull(leagueId, "League ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(displayName, "Display name must not be null");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException(
                    "Display name must not be blank"
            );
        }
        Objects.requireNonNull(joinedAt, "Joined at must not be null");
    }
}
