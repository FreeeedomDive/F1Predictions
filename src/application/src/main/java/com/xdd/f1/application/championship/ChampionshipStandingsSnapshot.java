package com.xdd.f1.application.championship;

import java.util.List;

public record ChampionshipStandingsSnapshot(int round, List<String> orderedDrivers) {
}
