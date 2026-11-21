package com.xdd.f1.infrastructure.http.jolpica;

import com.xdd.f1.application.championship.ChampionshipStandingsProvider;
import com.xdd.f1.application.championship.ChampionshipStandingsSnapshot;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

@Component
public class JolpicaChampionshipStandingsProvider implements ChampionshipStandingsProvider {
    private final JolpicaClient client;

    public JolpicaChampionshipStandingsProvider(JolpicaClient client) {
        this.client = client;
    }

    @Override
    public Optional<ChampionshipStandingsSnapshot> loadStandings(int season) {
        var response = client.getDriverStandings(season);
        var data = response == null ? null : response.mrData();
        if (data == null || data.standingsTable() == null || data.standingsTable().standingsLists() == null) {
            throw new IllegalStateException("Jolpica response is missing the driver standings table");
        }
        var total = parseNumber(data.total(), "total");
        var limit = parseNumber(data.limit(), "limit");
        if (parseNumber(data.offset(), "offset") != 0 || total < 0 || limit <= 0 || total > limit) {
            throw new IllegalStateException("Jolpica returned a partial driver standings table");
        }
        var table = data.standingsTable();
        var lists = table.standingsLists();
        if (total == 0 && lists.isEmpty()) {
            return Optional.empty();
        }
        if (lists.size() != 1 || lists.getFirst() == null) {
            throw new IllegalStateException("Jolpica returned an unexpected number of driver standings lists");
        }
        var standings = lists.getFirst();
        var drivers = standings.driverStandings();
        if (drivers == null || drivers.size() != total) {
            throw new IllegalStateException("Jolpica returned an incomplete driver standings list");
        }
        if (total == 0) {
            return Optional.empty();
        }
        var round = parseNumber(table.round(), "round");
        if (parseNumber(table.season(), "season") != season || parseNumber(standings.season(), "season") != season
                || round <= 0 || parseNumber(standings.round(), "round") != round) {
            throw new IllegalStateException("Jolpica driver standings do not match the requested season or reported round");
        }

        var positions = new TreeMap<Integer, String>();
        var names = new HashSet<String>();
        for (var entry : drivers) {
            if (entry == null || entry.driver() == null) {
                throw new IllegalStateException("Jolpica returned a driver standings entry without a driver");
            }
            var position = parseNumber(entry.position(), "position");
            var name = driverName(entry.driver().driverId());
            if (position < 1 || position > total || positions.putIfAbsent(position, name) != null || !names.add(name)) {
                throw new IllegalStateException("Jolpica returned duplicate drivers or invalid positions");
            }
        }
        return Optional.of(new ChampionshipStandingsSnapshot(round, List.copyOf(positions.values())));
    }

    private int parseNumber(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Jolpica returned an invalid " + field, exception);
        }
    }

    private String driverName(String driverId) {
        if (driverId == null || driverId.isBlank() || driverId.endsWith("_")
                || driverId.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalStateException("Jolpica returned an invalid driver ID");
        }
        var name = driverId.substring(driverId.lastIndexOf('_') + 1);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
