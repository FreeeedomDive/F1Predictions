package com.xdd.f1.application.bingo;

import com.xdd.f1.domain.bingo.BingoCard;

import java.util.List;

public record BingoAdminDetails(int season, boolean structureEditable, boolean canCopyPrevious, List<BingoCard> cards) {
    public BingoAdminDetails {
        cards = List.copyOf(cards);
    }
}
