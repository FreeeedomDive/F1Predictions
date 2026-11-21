package com.xdd.f1.contract.bingo;

import java.util.Map;
import java.util.UUID;

public record BingoAdminCardDto(UUID id, int season, Map<String, BingoCardTranslationDto> translations,
                                BingoCardProbabilityDto probability, int requiredRepeats, int completedRepeats,
                                boolean completed) {
}
