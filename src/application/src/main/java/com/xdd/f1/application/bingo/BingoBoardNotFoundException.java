package com.xdd.f1.application.bingo;

import com.xdd.f1.domain.bingo.BingoBoardId;

public class BingoBoardNotFoundException extends RuntimeException {
    public BingoBoardNotFoundException(BingoBoardId id) {
        super("Bingo board not found with id: " + id.value());
    }
}
