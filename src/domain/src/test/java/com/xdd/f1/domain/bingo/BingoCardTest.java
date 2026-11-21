package com.xdd.f1.domain.bingo;

import com.xdd.f1.domain.error.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BingoCardTest {
    private static final Map<String, BingoCardTranslation> ENGLISH = Map.of(
            "en", new BingoCardTranslation("Event", null));

    @Test
    void derivesCompletionFromTheCounter() {
        var card = card(3, 0, ENGLISH);
        assertThat(card.isCompleted()).isFalse();
        assertThat(card.updateProgress(2).isCompleted()).isFalse();
        var completed = card.updateProgress(3);
        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.updateProgress(1).isCompleted()).isFalse();
        assertThat(completed.updateProgress(1).translations()).isEqualTo(card.translations());
        assertThat(card.completedRepeats()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "-1, 0", "2, -1", "2, 3"})
    void rejectsImpossibleProgress(int required, int completed) {
        assertThatThrownBy(() -> card(required, completed, ENGLISH)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> card(2, 0, ENGLISH).updateProgress(-1)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void requiresOnlyAnEnglishDescription() {
        for (var translations : List.of(
                Map.<String, BingoCardTranslation>of(),
                Map.of("ru", new BingoCardTranslation("Событие", null)),
                Map.of("en", new BingoCardTranslation(null, null)),
                Map.of("en", new BingoCardTranslation(" ", null))
        )) {
            assertThatThrownBy(() -> card(1, 0, translations)).isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("English");
        }

        assertThat(card(1, 0, ENGLISH).translations()).isEqualTo(ENGLISH);
        assertThat(card(1, 0, Map.of("ru", new BingoCardTranslation("Событие", "Условие"),
                "en", new BingoCardTranslation("Event", null))).translations().get("en").explanation()).isNull();
        assertThat(card(1, 0, Map.of("en", new BingoCardTranslation("Event", "Condition"))).translations()
                .get("en").explanation()).isEqualTo("Condition");
    }

    @Test
    void keepsAllTranslationsWithoutSharingMutableState() {
        var translations = new HashMap<>(Map.of(
                "ru", new BingoCardTranslation("Событие", null),
                "en", new BingoCardTranslation("Event", null),
                "de", new BingoCardTranslation("Ereignis", null)));
        var card = card(1, 0, translations);
        translations.clear();

        assertThat(card.translations()).containsOnlyKeys("ru", "en", "de");
        assertThatThrownBy(() -> card.translations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static BingoCard card(int required, int completed, Map<String, BingoCardTranslation> translations) {
        return new BingoCard(new BingoCardId(UUID.randomUUID()), 2026,
                translations, BingoCardProbability.MEDIUM, required, completed);
    }
}
