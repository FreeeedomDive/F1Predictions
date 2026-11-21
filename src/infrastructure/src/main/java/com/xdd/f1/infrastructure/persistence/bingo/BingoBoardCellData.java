package com.xdd.f1.infrastructure.persistence.bingo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record BingoBoardCellData(
        @Column(name = "season", nullable = false)
        int season,
        @Column(name = "card_id", nullable = false)
        UUID cardId
) {
}
