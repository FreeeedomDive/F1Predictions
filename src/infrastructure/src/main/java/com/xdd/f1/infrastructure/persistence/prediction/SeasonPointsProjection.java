package com.xdd.f1.infrastructure.persistence.prediction;

import java.time.Instant;
import java.util.UUID;

public interface SeasonPointsProjection {
    UUID getPredictionId();

    String getRulesVersion();

    int getTotalPoints();

    Integer getTenthPlacePoints();

    Integer getDnfPoints();

    Integer getSafetyCarsPoints();

    Integer getFirstPlaceLeadPoints();

    Integer getDriverPositionPoints();

    Integer getTeamMatesPoints();

    UUID getParticipantId();

    String getParticipantName();

    Instant getParticipantJoinedAt();

    UUID getRaceId();

    int getSeason();

    int getRound();

    String getType();

    String getName();

    String getStatus();

    Instant getOpenAt();

    Instant getCloseAt();
}
