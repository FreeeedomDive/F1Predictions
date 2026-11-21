package com.xdd.f1.application.bingo;

import com.xdd.f1.domain.bingo.BingoBoard;
import com.xdd.f1.domain.bingo.BingoBoardId;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;

import java.util.List;
import java.util.Optional;

public interface BingoBoardRepository {
    Optional<BingoBoard> findById(BingoBoardId id);
    Optional<BingoBoard> findByLeagueAndSeasonAndParticipant(LeagueId leagueId, int season, LeagueParticipantId participantId);
    List<BingoBoard> findAllBySeason(int season);
    boolean existsBySeason(int season);
    void create(BingoBoard board);
    void markAchieved(BingoBoardId id);
}
