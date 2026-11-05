package com.xdd.f1.application.race;

import java.util.List;

public interface RaceCalendarProvider {
    List<RaceCalendarEntry> loadSeason(int season);
}
