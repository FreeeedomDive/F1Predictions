package com.xdd.f1.contract.bingo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ImportBingoCardsRequestDto(List<Card> cards) {
    public record Card(UUID id, Map<String, BingoCardTranslationDto> translations,
                       BingoCardProbabilityDto probability, Integer requiredRepeats, Integer completedRepeats) {
    }
}
