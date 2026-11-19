package com.xdd.f1.infrastructure.persistence.championship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChampionshipPredictionJpaRepository extends JpaRepository<ChampionshipPredictionEntity, ChampionshipPredictionEntityId> {
    List<ChampionshipPredictionEntity> findAllByIdSeason(int season);
    List<ChampionshipPredictionEntity> findAllByIdLeagueIdAndIdSeason(UUID leagueId, int season);
}
