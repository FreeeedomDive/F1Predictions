package com.xdd.f1.application.bingo;

import com.xdd.f1.domain.bingo.BingoCardId;

public class BingoCardNotFoundException extends RuntimeException {
    public BingoCardNotFoundException(BingoCardId id) {
        super("Bingo card not found with id: " + id.value());
    }
}
