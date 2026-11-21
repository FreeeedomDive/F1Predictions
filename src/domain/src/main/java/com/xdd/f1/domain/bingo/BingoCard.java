package com.xdd.f1.domain.bingo;

import com.xdd.f1.domain.error.DomainValidationException;

import java.util.Map;
import java.util.Objects;

public record BingoCard(
        BingoCardId id,
        int season,
        Map<String, BingoCardTranslation> translations,
        BingoCardProbability probability,
        int requiredRepeats,
        int completedRepeats
) {
    public BingoCard {
        Objects.requireNonNull(id, "Bingo card ID must not be null");
        Objects.requireNonNull(probability, "Bingo card probability must not be null");
        if (season <= 0) {
            throw new DomainValidationException("Bingo season must be greater than zero");
        }
        if (requiredRepeats <= 0) {
            throw new DomainValidationException("Required repeats must be greater than zero");
        }
        if (completedRepeats < 0 || completedRepeats > requiredRepeats) {
            throw new DomainValidationException("Completed repeats must be between zero and required repeats");
        }
        translations = Map.copyOf(translations);
        var en = translations.get("en");
        if (en == null || en.description() == null || en.description().isBlank()) {
            throw new DomainValidationException("Bingo card description is required in English");
        }
    }

    public boolean isCompleted() {
        return completedRepeats == requiredRepeats;
    }

    public BingoCard updateProgress(int newCompletedRepeats) {
        return new BingoCard(id, season, translations, probability, requiredRepeats, newCompletedRepeats);
    }
}
