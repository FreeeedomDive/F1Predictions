package com.xdd.f1.application.championship;

import com.xdd.f1.domain.championship.ChampionshipSeason;

import java.util.Optional;

public interface ChampionshipSeasonRepository {
    Optional<ChampionshipSeason> findBySeason(int season);
    void save(ChampionshipSeason season);
}
