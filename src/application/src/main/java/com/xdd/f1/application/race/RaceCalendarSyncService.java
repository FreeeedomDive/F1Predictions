package com.xdd.f1.application.race;

import org.springframework.stereotype.Service;

@Service
public class RaceCalendarSyncService {

    private final RaceCalendarProvider calendarProvider;
    private final RaceService races;

    public RaceCalendarSyncService(RaceCalendarProvider calendarProvider, RaceService races) {
        this.calendarProvider = calendarProvider;
        this.races = races;
    }

    public void syncCalendar(int season) {
        var entries = calendarProvider.loadSeason(season);
        races.applyCalendar(season, entries);
    }
}
