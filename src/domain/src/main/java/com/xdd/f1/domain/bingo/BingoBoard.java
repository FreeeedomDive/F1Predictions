package com.xdd.f1.domain.bingo;

import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record BingoBoard(
        BingoBoardId id,
        LeagueId leagueId,
        int season,
        LeagueParticipantId participantId,
        List<BingoCardId> cards,
        boolean bingoAchieved
) {
    public BingoBoard {
        Objects.requireNonNull(id, "Bingo board ID must not be null");
        Objects.requireNonNull(leagueId, "League ID must not be null");
        Objects.requireNonNull(participantId, "League participant ID must not be null");
        if (season <= 0) {
            throw new DomainValidationException("Bingo season must be greater than zero");
        }
        cards = List.copyOf(cards);
        if (cards.size() != 25 || cards.stream().distinct().count() != 25) {
            throw new DomainValidationException("A bingo board must contain exactly 25 distinct cards");
        }
    }

    public BingoBoard updateAchievement(Set<BingoCardId> completedCards) {
        if (bingoAchieved || BingoCompletionsCalculator.completedLines(this, completedCards).isEmpty()) {
            return this;
        }
        return new BingoBoard(id, leagueId, season, participantId, cards, true);
    }
}
