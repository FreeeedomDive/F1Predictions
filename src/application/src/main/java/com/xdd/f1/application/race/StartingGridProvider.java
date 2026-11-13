package com.xdd.f1.application.race;

import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.lineup.Driver;

import java.util.List;

public interface StartingGridProvider {
    List<Driver> loadStartingGrid(Race race);
}
