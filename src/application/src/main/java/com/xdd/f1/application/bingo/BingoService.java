package com.xdd.f1.application.bingo;

import com.xdd.f1.application.league.LeagueAccessGuard;
import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.contract.bingo.BingoAchievedEvent;
import com.xdd.f1.domain.bingo.BingoBoard;
import com.xdd.f1.domain.bingo.BingoBoardId;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.bingo.BingoCompletionsCalculator;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.user.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BingoService {
    private final BingoCardRepository cards;
    private final BingoBoardRepository boards;
    private final LeagueRepository leagues;
    private final LeagueParticipantRepository participants;
    private final LeagueAccessGuard access;
    private final TransactionLock lock;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BingoService(BingoCardRepository cards, BingoBoardRepository boards, LeagueRepository leagues,
                        LeagueParticipantRepository participants, LeagueAccessGuard access, TransactionLock lock,
                        ApplicationEventPublisher events, Clock clock) {
        this.cards = cards;
        this.boards = boards;
        this.leagues = leagues;
        this.participants = participants;
        this.access = access;
        this.lock = lock;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<BingoBoardDetails> findOwn(UserId userId, LeagueId leagueId, int season) {
        var participant = access.checkAccess(userId, leagueId);
        if (season < firstSeason(leagueId)) {
            return Optional.empty();
        }
        return boards.findByLeagueAndSeasonAndParticipant(leagueId, season, participant.id())
                .map(board -> toDetails(board, cards.findAllBySeason(season)));
    }

    @Transactional
    public BingoBoardDetails createOwn(UserId userId, LeagueId leagueId, int season) {
        lock.acquire("bingo:" + season);
        var participant = access.checkAccess(userId, leagueId);
        if (season < firstSeason(leagueId)) {
            throw new DomainValidationException("Bingo season is earlier than the league's first season");
        }
        var existing = boards.findByLeagueAndSeasonAndParticipant(leagueId, season, participant.id());
        var seasonCards = cards.findAllBySeason(season);
        if (existing.isPresent()) {
            return toDetails(existing.get(), seasonCards);
        }
        if (season != activeSeason()) {
            throw new DomainValidationException("Bingo boards can only be created for the current season");
        }
        if (seasonCards.size() != 25) {
            throw new DomainValidationException("A bingo season must contain exactly 25 cards to create a board");
        }
        var order = new ArrayList<>(seasonCards.stream().map(BingoCard::id).toList());
        Collections.shuffle(order);
        var board = new BingoBoard(new BingoBoardId(UUID.randomUUID()), leagueId, season,
                participant.id(), order, false).updateAchievement(completedCards(seasonCards));
        boards.create(board);
        if (board.bingoAchieved()) {
            publishAchievement(board);
        }
        return toDetails(board, seasonCards);
    }

    @Transactional
    public BingoCard updateProgress(BingoCardId cardId, int completedRepeats) {
        int season = activeSeason();
        lock.acquire("bingo:" + season);
        var current = cards.findById(cardId).orElseThrow(() -> new BingoCardNotFoundException(cardId));
        if (current.season() != season) {
            throw new DomainValidationException("Bingo progress can only be updated for the current season");
        }
        var updated = current.updateProgress(completedRepeats);
        if (updated.equals(current)) {
            return current;
        }
        cards.updateProgress(cardId, completedRepeats);
        var completed = completedCards(cards.findAllBySeason(season));
        for (var board : boards.findAllBySeason(season)) {
            if (!board.bingoAchieved() && board.cards().contains(cardId)
                    && board.updateAchievement(completed).bingoAchieved()) {
                boards.markAchieved(board.id());
                publishAchievement(board);
            }
        }
        return updated;
    }

    private BingoBoardDetails toDetails(BingoBoard board, List<BingoCard> seasonCards) {
        var byId = seasonCards.stream().collect(Collectors.toMap(BingoCard::id, Function.identity()));
        return new BingoBoardDetails(board, board.cards().stream().map(byId::get).toList(),
                BingoCompletionsCalculator.completedLines(board, completedCards(seasonCards)));
    }

    private Set<BingoCardId> completedCards(List<BingoCard> seasonCards) {
        return seasonCards.stream().filter(BingoCard::isCompleted).map(BingoCard::id).collect(Collectors.toSet());
    }

    private void publishAchievement(BingoBoard board) {
        var participant = participants.findByLeagueAndId(board.leagueId(), board.participantId())
                .orElseThrow(() -> new LeagueParticipantNotFoundException(board.participantId()));
        events.publishEvent(new BingoAchievedEvent(board.leagueId().value(), board.season(), board.id().value(),
                board.participantId().value(), participant.displayName()));
    }

    private int firstSeason(LeagueId leagueId) {
        return leagues.findById(leagueId).orElseThrow(() -> new LeagueNotFoundException(leagueId))
                .settings().firstSeason();
    }

    private int activeSeason() {
        return Year.now(clock.withZone(ZoneOffset.UTC)).getValue();
    }
}
