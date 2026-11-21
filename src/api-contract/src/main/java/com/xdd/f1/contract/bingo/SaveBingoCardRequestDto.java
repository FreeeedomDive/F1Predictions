package com.xdd.f1.contract.bingo;

import java.util.Map;

public record SaveBingoCardRequestDto(Map<String, BingoCardTranslationDto> translations,
                                     BingoCardProbabilityDto probability, Integer requiredRepeats) {
}
