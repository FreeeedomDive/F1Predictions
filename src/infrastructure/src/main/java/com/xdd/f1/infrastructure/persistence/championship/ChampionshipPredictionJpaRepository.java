package com.xdd.f1.infrastructure.persistence.championship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChampionshipPredictionJpaRepository extends JpaRepository<ChampionshipPredictionEntity, ChampionshipPredictionEntityId> {
    List<ChampionshipPredictionEntity> findAllByIdSeason(int season);
    List<ChampionshipPredictionEntity> findAllByIdLeagueIdAndIdSeason(UUID leagueId, int season);

    @Modifying
    @Query("""
        delete from ChampionshipPredictionEntity prediction
        where prediction.id.season = :season
            and prediction.id.stage = 'MID_SEASON'
            and prediction.predictionSource = 'PRE_SEASON_COPY'
        """)
    void deletePreSeasonCopies(@Param("season") int season);
}
