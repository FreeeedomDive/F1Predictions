package com.xdd.f1.infrastructure.persistence.league;

import java.util.UUID;

public interface ParticipantDiscordIdentityProjection {
    UUID getParticipantId();

    String getDiscordId();
}
