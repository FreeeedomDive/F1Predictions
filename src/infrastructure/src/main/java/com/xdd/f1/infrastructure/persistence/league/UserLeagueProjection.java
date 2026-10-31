package com.xdd.f1.infrastructure.persistence.league;

import java.util.UUID;

public interface UserLeagueProjection {

    UUID getLeagueId();

    UUID getParticipantId();

    String getLeagueName();

    String getParticipantName();
}