package com.xdd.f1.contract.user;

import java.util.UUID;

public record UserLeagueDto(UUID leagueId, UUID participantId, String leagueName, String participantName) {
}
