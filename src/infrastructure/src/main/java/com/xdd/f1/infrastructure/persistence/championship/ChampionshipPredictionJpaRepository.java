package com.xdd.f1.infrastructure.persistence.championship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChampionshipPredictionJpaRepository extends JpaRepository<ChampionshipPredictionEntity, ChampionshipPredictionEntityId> {
    List<ChampionshipPredictionEntity> findAllByIdSeason(int season);
}
