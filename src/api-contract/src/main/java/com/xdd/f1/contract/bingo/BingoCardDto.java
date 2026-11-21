package com.xdd.f1.contract.bingo;

import java.util.UUID;

public record BingoCardDto(UUID id, String description, String explanation, String language,
                           BingoCardProbabilityDto probability, int requiredRepeats, int completedRepeats, boolean completed) {
}
