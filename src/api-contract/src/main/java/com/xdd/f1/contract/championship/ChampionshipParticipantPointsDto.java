package com.xdd.f1.contract.championship;

import java.util.UUID;

public record ChampionshipParticipantPointsDto(UUID participantId, String displayName, int totalPoints) {
}
