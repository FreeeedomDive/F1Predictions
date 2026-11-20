package com.xdd.f1.application.championship;

import java.util.Optional;

public interface ChampionshipStandingsProvider {
    Optional<ChampionshipStandingsSnapshot> loadStandings(int season);
}
