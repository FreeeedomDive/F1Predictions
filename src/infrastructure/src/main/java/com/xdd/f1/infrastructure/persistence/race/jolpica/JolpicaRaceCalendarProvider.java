package com.xdd.f1.infrastructure.persistence.race.jolpica;

import com.xdd.f1.application.race.RaceCalendarEntry;
import com.xdd.f1.application.race.RaceCalendarProvider;
import com.xdd.f1.domain.race.RaceType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class JolpicaRaceCalendarProvider implements RaceCalendarProvider {

    private final JolpicaClient client;

    public JolpicaRaceCalendarProvider(JolpicaClient client) {
        this.client = client;
    }

    @Override
    public List<RaceCalendarEntry> loadSeason(int season) {
        var response = client.getSeason(season);

        return response.mrData().raceTable().races().stream()
                .flatMap(race -> mapRace(race).stream())
                .toList();
    }

    private List<RaceCalendarEntry> mapRace(JolpicaRace race) {
        var season = Integer.parseInt(race.season());
        var round = Integer.parseInt(race.round());
        var entries = new ArrayList<RaceCalendarEntry>(2);

        entries.add(new RaceCalendarEntry(
                season,
                round,
                RaceType.RACE,
                race.raceName(),
                toInstant(race.qualifying()),
                toInstant(race.date(), race.time())
        ));

        if (race.sprintQualifying() != null && race.sprint() != null) {
            entries.add(new RaceCalendarEntry(
                    season,
                    round,
                    RaceType.SPRINT,
                    race.raceName(),
                    toInstant(race.sprintQualifying()),
                    toInstant(race.sprint())
            ));
        }

        return entries;
    }

    private Instant toInstant(JolpicaSession session) {
        return toInstant(session.date(), session.time());
    }

    private Instant toInstant(String date, String time) {
        return Instant.parse(date + "T" + time);
    }
}
