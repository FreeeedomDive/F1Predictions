package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoDetails;
import com.xdd.f1.contract.bingo.BingoBoardDto;
import com.xdd.f1.contract.bingo.BingoCardDto;
import com.xdd.f1.contract.bingo.BingoCardProbabilityDto;
import com.xdd.f1.contract.bingo.BingoDetailsDto;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.error.DomainValidationException;

import java.util.IllformedLocaleException;
import java.util.Locale;

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
