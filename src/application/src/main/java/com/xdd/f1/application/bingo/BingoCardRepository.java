package com.xdd.f1.application.bingo;

import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCardId;

import java.util.List;
import java.util.Optional;

public interface BingoCardRepository {
    Optional<BingoCard> findById(BingoCardId id);
    List<BingoCard> findAllBySeason(int season);
    void create(BingoCard card);
    void updateDefinition(BingoCard card);
    void delete(BingoCardId id);
    void updateProgress(BingoCardId id, int completedRepeats);
}
