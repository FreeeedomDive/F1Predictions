package com.xdd.f1.contract.bingo;

import java.util.List;
import java.util.UUID;

public record BingoBoardDto(UUID id, UUID participantId, boolean bingoAchieved,
                            List<BingoCardDto> cards) {
}
