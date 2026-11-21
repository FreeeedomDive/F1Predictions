package com.xdd.f1.application.bingo;

import com.xdd.f1.domain.bingo.BingoBoard;
import com.xdd.f1.domain.bingo.BingoCard;

import java.util.List;

public record BingoBoardDetails(BingoBoard board, List<BingoCard> cards, List<List<Integer>> completedLines) {
    public BingoBoardDetails {
        cards = List.copyOf(cards);
        completedLines = completedLines.stream().map(List::copyOf).toList();
    }
}
