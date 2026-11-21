package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoAdminDetails;
import com.xdd.f1.application.bingo.BingoDetails;
import com.xdd.f1.contract.bingo.BingoAdminCardDto;
import com.xdd.f1.contract.bingo.BingoAdminDetailsDto;
import com.xdd.f1.contract.bingo.BingoBoardDto;
import com.xdd.f1.contract.bingo.BingoCardDto;
import com.xdd.f1.contract.bingo.BingoCardProbabilityDto;
import com.xdd.f1.contract.bingo.BingoCardTranslationDto;
import com.xdd.f1.contract.bingo.BingoDetailsDto;
import com.xdd.f1.contract.bingo.SaveBingoCardRequestDto;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCardProbability;
import com.xdd.f1.domain.bingo.BingoCardTranslation;
import com.xdd.f1.domain.error.DomainValidationException;

import java.util.HashMap;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class BingoDtoMapper {
    private BingoDtoMapper() {
    }

    static String normalizeLanguage(String language) {
        try {
            var locale = new Locale.Builder().setLanguageTag(language).build();
            if (!locale.getLanguage().isEmpty()) {
                return locale.toLanguageTag();
            }
        } catch (IllformedLocaleException exception) {
            throw new DomainValidationException("Bingo language must be a valid language tag");
        }
        throw new DomainValidationException("Bingo language must be a valid language tag");
    }

    static BingoDetailsDto toDto(BingoDetails details, String language) {
        var board = details.board();
        return new BingoDetailsDto(details.season(), details.activeSeason(), details.canCreate(),
                board == null ? null : new BingoBoardDto(board.board().id().value(), board.board().participantId().value(),
                        board.board().bingoAchieved(), board.cards().stream().map(card -> toDto(card, language)).toList()));
    }

    static BingoAdminDetailsDto toDto(BingoAdminDetails details) {
        return new BingoAdminDetailsDto(details.season(), details.structureEditable(), details.canCopyPrevious(),
                details.cards().stream().map(BingoDtoMapper::toAdminDto).toList());
    }

    static BingoAdminCardDto toAdminDto(BingoCard card) {
        var translations = card.translations().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new BingoCardTranslationDto(entry.getValue().description(), entry.getValue().explanation())
        ));
        return new BingoAdminCardDto(card.id().value(), card.season(), translations,
                BingoCardProbabilityDto.valueOf(card.probability().name()),
                card.requiredRepeats(), card.completedRepeats(), card.isCompleted());
    }

    static void validate(SaveBingoCardRequestDto request) {
        if (request.translations() == null || request.probability() == null || request.requiredRepeats() == null) {
            throw new DomainValidationException("Bingo card translations, probability and required repeats are required");
        }
    }

    static Map<String, BingoCardTranslation> toTranslations(Map<String, BingoCardTranslationDto> translations) {
        var result = new HashMap<String, BingoCardTranslation>();
        for (var entry : translations.entrySet()) {
            if (entry.getValue() == null) {
                throw new DomainValidationException("Bingo card translation must not be null");
            }
            var language = normalizeLanguage(entry.getKey());
            var translation = new BingoCardTranslation(entry.getValue().description(), entry.getValue().explanation());
            if (result.put(language, translation) != null) {
                throw new DomainValidationException("Duplicate bingo card translation language: " + language);
            }
        }
        return result;
    }

    static BingoCardProbability toDomain(BingoCardProbabilityDto probability) {
        return BingoCardProbability.valueOf(probability.name());
    }

    private static BingoCardDto toDto(BingoCard card, String language) {
        var translation = card.translations().get(language);
        if (translation == null || translation.description() == null || translation.description().isBlank()) {
            language = "en";
            translation = card.translations().get(language);
        }
        return new BingoCardDto(card.id().value(), translation.description(), translation.explanation(), language,
                BingoCardProbabilityDto.valueOf(card.probability().name()),
                card.requiredRepeats(), card.completedRepeats(), card.isCompleted());
    }
}
