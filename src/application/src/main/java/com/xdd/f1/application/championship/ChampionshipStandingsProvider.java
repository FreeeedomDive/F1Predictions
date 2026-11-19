package com.xdd.f1.application.championship;

import java.util.List;
import java.util.Optional;

public interface ChampionshipStandingsProvider {
    Optional<Snapshot> loadStandings(int season);

    record Snapshot(int round, List<String> orderedDrivers) {
    }
}
