package com.xdd.f1.domain.bingo;

import java.util.List;
import java.util.Set;

public final class BingoCompletionsCalculator {
    private static final List<List<Integer>> LINES = List.of(
            List.of(0, 1, 2, 3, 4),
            List.of(5, 6, 7, 8, 9),
            List.of(10, 11, 12, 13, 14),
            List.of(15, 16, 17, 18, 19),
            List.of(20, 21, 22, 23, 24),
            List.of(0, 5, 10, 15, 20),
            List.of(1, 6, 11, 16, 21),
            List.of(2, 7, 12, 17, 22),
            List.of(3, 8, 13, 18, 23),
            List.of(4, 9, 14, 19, 24),
            List.of(0, 6, 12, 18, 24),
            List.of(4, 8, 12, 16, 20)
    );

    private BingoCompletionsCalculator() {
    }

    public static List<List<Integer>> completedLines(BingoBoard board, Set<BingoCardId> completedCards) {
        return LINES.stream()
                .filter(line -> line.stream().allMatch(position -> completedCards.contains(board.cards().get(position))))
                .toList();
    }
}
