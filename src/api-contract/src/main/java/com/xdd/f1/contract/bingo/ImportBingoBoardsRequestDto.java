package com.xdd.f1.contract.bingo;

import java.util.List;
import java.util.UUID;

public record ImportBingoBoardsRequestDto(List<Board> boards) {
    public record Board(UUID id, UUID participantId, List<UUID> cardIds, Boolean bingoAchieved) {
    }
}
