package com.xdd.f1.domain.bingo;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BingoCompletionsCalculatorTest {
    private static final List<BingoCardId> CARDS = IntStream.range(0, 25)
            .mapToObj(i -> new BingoCardId(new UUID(0, (i * 7) % 25 + 1))).toList();

    @ParameterizedTest
    @MethodSource("winningLines")
    void detectsEachOfTheTwelveWinningLines(List<Integer> line) {
        var board = board(CARDS, false);
        var completed = line.stream().map(CARDS::get).collect(Collectors.toSet());

        assertThat(BingoCompletionsCalculator.completedLines(board, completed)).containsExactly(line);
        assertThat(board.updateAchievement(completed).bingoAchieved()).isTrue();

        var incomplete = new HashSet<>(completed);
        incomplete.remove(CARDS.get(line.getFirst()));
        assertThat(BingoCompletionsCalculator.completedLines(board, incomplete)).isEmpty();
        assertThat(board.updateAchievement(incomplete)).isSameAs(board);
    }

    @Test
    void hasNoFreeCentreCell() {
        var board = board(CARDS, false);
        var diagonalWithoutCentre = Set.of(CARDS.get(0), CARDS.get(6), CARDS.get(18), CARDS.get(24));

        assertThat(BingoCompletionsCalculator.completedLines(board, diagonalWithoutCentre)).isEmpty();
        assertThat(BingoCompletionsCalculator.completedLines(board, Set.of())).isEmpty();
        assertThat(BingoCompletionsCalculator.completedLines(board, Set.copyOf(CARDS))).hasSize(12);
    }

    @Test
    void keepsTheFirstAchievementAfterCorrectionsAndDoesNotAchieveAgain() {
        var original = board(CARDS, false);
        var completed = Set.copyOf(CARDS.subList(0, 5));
        var achieved = original.updateAchievement(completed);

        assertThat(original.bingoAchieved()).isFalse();
        assertThat(achieved.bingoAchieved()).isTrue();
        assertThat(achieved.cards()).containsExactlyElementsOf(CARDS);
        assertThat(achieved.updateAchievement(completed)).isSameAs(achieved);
        assertThat(achieved.updateAchievement(Set.of())).isSameAs(achieved);
        assertThat(BingoCompletionsCalculator.completedLines(achieved, Set.of())).isEmpty();

        var imported = board(CARDS, true);
        assertThat(imported.updateAchievement(Set.of())).isSameAs(imported);
    }

    @Test
    void ignoresCompletedCardsOutsideTheBoard() {
        var completed = Set.of(new BingoCardId(UUID.randomUUID()));

        assertThat(BingoCompletionsCalculator.completedLines(board(CARDS, false), completed)).isEmpty();
    }

    @Test
    void requiresTwentyFiveDistinctCardsAndKeepsAnImmutableOrder() {
        assertThatThrownBy(() -> board(List.of(), false)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> board(CARDS.subList(0, 24), false)).isInstanceOf(DomainValidationException.class);
        var duplicate = new ArrayList<>(CARDS);
        duplicate.set(24, duplicate.getFirst());
        assertThatThrownBy(() -> board(duplicate, false)).isInstanceOf(DomainValidationException.class);
        var tooMany = new ArrayList<>(CARDS);
        tooMany.add(new BingoCardId(UUID.randomUUID()));
        assertThatThrownBy(() -> board(tooMany, false)).isInstanceOf(DomainValidationException.class);

        var source = new ArrayList<>(CARDS);
        var board = board(source, false);
        source.clear();
        assertThat(board.cards()).containsExactlyElementsOf(CARDS);
        assertThatThrownBy(() -> board.cards().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static BingoBoard board(List<BingoCardId> cards, boolean achieved) {
        return new BingoBoard(new BingoBoardId(UUID.randomUUID()), new LeagueId(UUID.randomUUID()), 2026,
                new LeagueParticipantId(UUID.randomUUID()), cards, achieved);
    }

    private static Stream<List<Integer>> winningLines() {
        return Stream.of(
                "0 1 2 3 4", "5 6 7 8 9", "10 11 12 13 14", "15 16 17 18 19", "20 21 22 23 24",
                "0 5 10 15 20", "1 6 11 16 21", "2 7 12 17 22", "3 8 13 18 23", "4 9 14 19 24",
                "0 6 12 18 24", "4 8 12 16 20"
        ).map(line -> Arrays.stream(line.split(" ")).map(Integer::parseInt).toList());
    }
}
