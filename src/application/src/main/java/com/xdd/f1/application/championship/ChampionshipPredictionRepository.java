package com.xdd.f1.application.championship;

import com.xdd.f1.domain.championship.ChampionshipPrediction;
import com.xdd.f1.domain.championship.ChampionshipStage;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;

import java.util.List;
import java.util.Optional;

public interface ChampionshipPredictionRepository {
    Optional<ChampionshipPrediction> findByLeagueAndSeasonAndParticipantAndStage(
            LeagueId leagueId, int season, LeagueParticipantId participantId, ChampionshipStage stage
    );

    List<ChampionshipPrediction> findAllBySeason(int season);
    List<ChampionshipPrediction> findAllByLeagueAndSeason(LeagueId leagueId, int season);
    void save(ChampionshipPrediction prediction);
}
