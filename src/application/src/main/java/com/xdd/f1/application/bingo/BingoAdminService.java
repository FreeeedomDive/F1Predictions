package com.xdd.f1.application.bingo;

import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.bingo.BingoCardProbability;
import com.xdd.f1.domain.bingo.BingoCardTranslation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
public class BingoAdminService {
    private final BingoCardRepository cards;
    private final BingoBoardRepository boards;
    private final TransactionLock lock;
    private final Clock clock;

    public BingoAdminService(BingoCardRepository cards, BingoBoardRepository boards, TransactionLock lock, Clock clock) {
        this.cards = cards;
        this.boards = boards;
        this.lock = lock;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BingoAdminDetails getDetails() {
        int season = activeSeason();
        var current = cards.findAllBySeason(season);
        return new BingoAdminDetails(season, !boards.existsBySeason(season),
                current.isEmpty() && cards.findAllBySeason(season - 1).size() == 25, current);
    }

    @Transactional
    public BingoCard createCard(Map<String, BingoCardTranslation> translations,
                                BingoCardProbability probability, int requiredRepeats) {
        int season = activeSeason();
        lock.acquire("bingo:" + season);
        requireEditableStructure(season);
        if (cards.findAllBySeason(season).size() >= 25) {
            throw new BingoConflictException("A bingo season cannot contain more than 25 cards");
        }
        var card = new BingoCard(new BingoCardId(UUID.randomUUID()), season, translations, probability, requiredRepeats, 0);
        cards.create(card);
        return card;
    }

    @Transactional
    public BingoCard updateCard(BingoCardId id, Map<String, BingoCardTranslation> translations,
                                BingoCardProbability probability, int requiredRepeats) {
        int season = activeSeason();
        lock.acquire("bingo:" + season);
        var current = currentCard(id, season);
        if (current.requiredRepeats() != requiredRepeats) {
            requireEditableStructure(season);
        }
        var updated = new BingoCard(id, season, translations, probability, requiredRepeats, current.completedRepeats());
        cards.updateDefinition(updated);
        return updated;
    }

    @Transactional
    public void deleteCard(BingoCardId id) {
        int season = activeSeason();
        lock.acquire("bingo:" + season);
        currentCard(id, season);
        requireEditableStructure(season);
        cards.delete(id);
    }

    @Transactional
    public BingoAdminDetails copyPrevious() {
        int season = activeSeason();
        lock.acquire("bingo:" + season);
        requireEditableStructure(season);
        if (!cards.findAllBySeason(season).isEmpty()) {
            throw new BingoConflictException("Bingo cards already exist for the current season");
        }
        var previous = cards.findAllBySeason(season - 1);
        if (previous.size() != 25) {
            throw new BingoConflictException("The previous bingo season must contain exactly 25 cards to copy");
        }
        for (var card : previous) {
            cards.create(new BingoCard(new BingoCardId(UUID.randomUUID()), season, card.translations(),
                    card.probability(), card.requiredRepeats(), 0));
        }
        return getDetails();
    }

    private BingoCard currentCard(BingoCardId id, int season) {
        var card = cards.findById(id).orElseThrow(() -> new BingoCardNotFoundException(id));
        if (card.season() != season) {
            throw new BingoConflictException("Only cards from the current bingo season can be edited");
        }
        return card;
    }

    private void requireEditableStructure(int season) {
        if (boards.existsBySeason(season)) {
            throw new BingoConflictException("Bingo card structure cannot be changed after a board has been created");
        }
    }

    private int activeSeason() {
        return Year.now(clock.withZone(ZoneOffset.UTC)).getValue();
    }
}
