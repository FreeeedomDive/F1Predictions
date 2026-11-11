package com.xdd.f1.contract.race.result;

import java.util.UUID;

public record RaceParticipantPointsDto(UUID participantId, String participantName, int totalPoints) {
}
