package com.xdd.f1.bootstrap.bingo;

import com.xdd.f1.application.bingo.BingoImportService;
import com.xdd.f1.contract.bingo.BingoImportResultDto;
import com.xdd.f1.contract.bingo.ImportBingoBoardsRequestDto;
import com.xdd.f1.contract.bingo.ImportBingoCardsRequestDto;
import com.xdd.f1.domain.bingo.BingoBoard;
import com.xdd.f1.domain.bingo.BingoBoardId;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.error.DomainValidationException;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class BingoImportController {
    private final BingoImportService imports;

    public BingoImportController(BingoImportService imports) {
        this.imports = imports;
    }

    @PutMapping("/importApi/v1/bingo/{season}/cards")
    public ResponseEntity<BingoImportResultDto> cards(@PathVariable int season,
                                                     @RequestBody ImportBingoCardsRequestDto request) {
        if (request.cards() == null || request.cards().isEmpty()) {
            throw new DomainValidationException("Bingo cards are required");
        }
        var cards = request.cards().stream().map(card -> {
            if (card == null || card.id() == null || card.translations() == null || card.probability() == null
                    || card.requiredRepeats() == null || card.completedRepeats() == null) {
                throw new DomainValidationException("Bingo card ID, translations, probability and repeat counts are required");
            }
            return new BingoCard(new BingoCardId(card.id()), season, BingoDtoMapper.toTranslations(card.translations()),
                    BingoDtoMapper.toDomain(card.probability()), card.requiredRepeats(), card.completedRepeats());
        }).toList();
        int created = imports.importCards(season, cards);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new BingoImportResultDto(created, cards.size() - created));
    }

    @PutMapping("/importApi/v1/leagues/{leagueId}/bingo/{season}/boards")
    public ResponseEntity<BingoImportResultDto> boards(@PathVariable UUID leagueId, @PathVariable int season,
                                                      @RequestBody ImportBingoBoardsRequestDto request) {
        if (request.boards() == null || request.boards().isEmpty()) {
            throw new DomainValidationException("Bingo boards are required");
        }
        var boards = request.boards().stream().map(board -> {
            if (board == null || board.id() == null || board.participantId() == null || board.cardIds() == null
                    || board.cardIds().contains(null) || board.bingoAchieved() == null) {
                throw new DomainValidationException("Bingo board ID, participant ID, card IDs and achievement are required");
            }
            return new BingoBoard(new BingoBoardId(board.id()), new LeagueId(leagueId), season,
                    new LeagueParticipantId(board.participantId()),
                    board.cardIds().stream().map(BingoCardId::new).toList(), board.bingoAchieved());
        }).toList();
        int created = imports.importBoards(new LeagueId(leagueId), season, boards);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new BingoImportResultDto(created, boards.size() - created));
    }
}
