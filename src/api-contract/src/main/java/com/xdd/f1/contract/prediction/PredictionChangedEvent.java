package com.xdd.f1.contract.prediction;

import java.util.UUID;

public record PredictionChangedEvent(UUID leagueId, UUID participantId, String participantName,
                                     UUID raceId, String raceName, String raceType, boolean created) {
}
