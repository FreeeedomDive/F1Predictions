package com.xdd.f1.application.bingo;

import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.domain.bingo.BingoBoard;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCompletionsCalculator;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BingoImportService {
    private final BingoCardRepository cards;
    private final BingoBoardRepository boards;
    private final LeagueRepository leagues;
    private final LeagueParticipantRepository participants;
    private final TransactionLock lock;

    public BingoImportService(BingoCardRepository cards, BingoBoardRepository boards, LeagueRepository leagues,
                              LeagueParticipantRepository participants, TransactionLock lock) {
        this.cards = cards;
        this.boards = boards;
        this.leagues = leagues;
        this.participants = participants;
        this.lock = lock;
    }

    @Transactional
    public int importCards(int season, List<BingoCard> imported) {
        lock.acquire("bingo:" + season);
        if (imported.isEmpty() || imported.stream().map(BingoCard::id).distinct().count() != imported.size()
                || imported.stream().anyMatch(card -> card.season() != season)) {
            throw new DomainValidationException("Import must contain distinct bingo cards from the requested season");
        }
        var created = new ArrayList<BingoCard>();
        for (var card : imported) {
            var existing = cards.findById(card.id());
            if (existing.isPresent()) {
                if (!existing.get().equals(card)) {
                    throw new BingoConflictException("Bingo card already exists with different data: " + card.id().value());
                }
            } else {
                created.add(card);
            }
        }
        if (!created.isEmpty()) {
            if (boards.existsBySeason(season)) {
                throw new BingoConflictException("Bingo cards cannot be added after a board has been created");
            }
            if (cards.findAllBySeason(season).size() + created.size() > 25) {
                throw new BingoConflictException("A bingo season cannot contain more than 25 cards");
            }
            created.forEach(cards::create);
        }
        return created.size();
    }

    @Transactional
    public int importBoards(LeagueId leagueId, int season, List<BingoBoard> imported) {
        lock.acquire("bingo:" + season);
        var league = leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId));
        if (season < league.settings().firstSeason()) {
            throw new BingoConflictException("Bingo season is earlier than the league's first season");
        }
        if (imported.isEmpty() || imported.stream().map(BingoBoard::id).distinct().count() != imported.size()
                || imported.stream().map(BingoBoard::participantId).distinct().count() != imported.size()
                || imported.stream().anyMatch(board -> board.season() != season || !board.leagueId().equals(leagueId))) {
            throw new DomainValidationException("Import must contain distinct bingo boards from the requested league and season");
        }
        var seasonCards = cards.findAllBySeason(season);
        var cardIds = seasonCards.stream().map(BingoCard::id).collect(Collectors.toSet());
        if (cardIds.size() != 25) {
            throw new BingoConflictException("Import all 25 bingo cards before importing boards");
        }
        var completed = seasonCards.stream().filter(BingoCard::isCompleted).map(BingoCard::id).collect(Collectors.toSet());
        var created = new ArrayList<BingoBoard>();
        for (var board : imported) {
            participants.findByLeagueAndId(leagueId, board.participantId())
                    .orElseThrow(() -> new LeagueParticipantNotFoundException(board.participantId()));
            if (!cardIds.containsAll(board.cards())) {
                throw new BingoConflictException("Bingo board contains unknown cards or cards from another season: " + board.id().value());
            }
            if (!board.bingoAchieved() && !BingoCompletionsCalculator.completedLines(board, completed).isEmpty()) {
                throw new BingoConflictException("Bingo board has a completed line but bingoAchieved is false: " + board.id().value());
            }
            var byId = boards.findById(board.id());
            var byParticipant = boards.findByLeagueAndSeasonAndParticipant(leagueId, season, board.participantId());
            if (byId.isPresent() && !byId.get().equals(board)
                    || byParticipant.isPresent() && !byParticipant.get().equals(board)) {
                throw new BingoConflictException("Bingo board already exists with different data: " + board.id().value());
            }
            if (byId.isEmpty()) {
                created.add(board);
            }
        }
        created.forEach(boards::create);
        return created.size();
    }
}
