package com.xdd.f1.infrastructure.persistence.league;

import java.util.UUID;

public interface LeagueParticipantNameProjection {
    UUID getId();
    String getDisplayName();
}
