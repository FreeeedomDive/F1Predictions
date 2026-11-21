package com.xdd.f1.contract.bingo;

import java.util.UUID;

public record BingoAchievedEvent(UUID leagueId, int season, UUID boardId, UUID participantId, String displayName) {
}
