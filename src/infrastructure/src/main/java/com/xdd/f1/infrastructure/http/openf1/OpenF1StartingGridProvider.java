package com.xdd.f1.infrastructure.http.openf1;

import com.xdd.f1.application.race.StartingGridProvider;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class OpenF1StartingGridProvider implements StartingGridProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenF1StartingGridProvider.class);
    private static final Duration DATE_TOLERANCE = Duration.ofHours(24);
    private static final Duration PUBLIC_ACCESS_DELAY = Duration.ofMinutes(30);
    private static final Pattern BROADCAST_NAME = Pattern.compile("^\\p{L}\\.?\\s+(.+)$");

    private final OpenF1Client client;
    private final Clock clock;

    public OpenF1StartingGridProvider(OpenF1Client client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public List<Driver> loadStartingGrid(Race race) {
        try {
            var qualifying = findQualifyingSession(race, client.getSessions(race.season()));
            if (qualifying.isEmpty()) {
                return List.of();
            }
            var session = qualifying.get();
            if (session.dateEnd() == null || session.dateEnd().isBefore(session.dateStart())
                    || clock.instant().isBefore(session.dateEnd().plus(PUBLIC_ACCESS_DELAY))) {
                return List.of();
            }
            var grid = client.getStartingGrid(session.sessionKey());
            if (grid == null || grid.isEmpty()) {
                return List.of();
            }
            return mapGrid(session.sessionKey(), grid, client.getDrivers(session.sessionKey()));
        } catch (HttpClientErrorException.NotFound exception) {
            return List.of();
        }
    }

    private Optional<OpenF1Session> findQualifyingSession(Race race, List<OpenF1Session> sessions) {
        if (sessions == null) {
            return Optional.empty();
        }
        var raceName = race.type() == RaceType.SPRINT ? "Sprint" : "Race";
        var raceSessions = sessions.stream()
                .filter(this::isAvailableSession)
                .filter(session -> raceName.equals(session.sessionName()) && near(session.dateStart(), race.closeAt()))
                .toList();
        if (raceSessions.size() != 1) {
            if (raceSessions.size() > 1) {
                LOGGER.warn("Multiple OpenF1 {} sessions match race {}", raceName, race.id().value());
            }
            return Optional.empty();
        }
        var raceSession = raceSessions.getFirst();
        var qualifyingName = race.type() == RaceType.SPRINT ? "Sprint Qualifying" : "Qualifying";
        var qualifyingSessions = sessions.stream()
                .filter(this::isAvailableSession)
                .filter(session -> qualifyingName.equals(session.sessionName()))
                .filter(session -> session.meetingKey() == raceSession.meetingKey())
                .filter(session -> near(session.dateStart(), race.openAt()) && session.dateStart().isBefore(raceSession.dateStart()))
                .toList();
        if (qualifyingSessions.size() > 1) {
            LOGGER.warn("Multiple OpenF1 {} sessions match race {}", qualifyingName, race.id().value());
        }
        return qualifyingSessions.size() == 1 ? Optional.of(qualifyingSessions.getFirst()) : Optional.empty();
    }

    private boolean isAvailableSession(OpenF1Session session) {
        return session != null && !Boolean.TRUE.equals(session.cancelled()) && session.sessionKey() > 0
                && session.meetingKey() > 0 && session.dateStart() != null;
    }

    private boolean near(Instant actual, Instant expected) {
        return !actual.isBefore(expected.minus(DATE_TOLERANCE)) && !actual.isAfter(expected.plus(DATE_TOLERANCE));
    }

    private List<Driver> mapGrid(int sessionKey, List<OpenF1GridEntry> grid, List<OpenF1Driver> drivers) {
        if (drivers == null) {
            return invalidGrid(sessionKey);
        }
        var driversByNumber = drivers.stream()
                .collect(Collectors.toMap(OpenF1Driver::driverNumber, Function.identity()));
        var ordered = grid.stream()
                .sorted(Comparator.comparing(OpenF1GridEntry::position, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        var result = new ArrayList<Driver>(ordered.size());
        for (var index = 0; index < ordered.size(); index++) {
            var entry = ordered.get(index);
            var driver = toDriver(driversByNumber.get(entry.driverNumber()));
            if (!Objects.equals(entry.position(), index + 1) || driver == null) {
                return invalidGrid(sessionKey);
            }
            result.add(driver);
        }
        if (result.stream().distinct().count() != result.size()) {
            return invalidGrid(sessionKey);
        }
        return List.copyOf(result);
    }

    private Driver toDriver(OpenF1Driver driver) {
        if (driver == null || driver.broadcastName() == null) {
            return null;
        }
        var name = BROADCAST_NAME.matcher(driver.broadcastName().strip());
        if (!name.matches()) {
            return null;
        }
        var surname = name.group(1).strip().toLowerCase(Locale.ROOT);
        return new Driver(surname.substring(0, 1).toUpperCase(Locale.ROOT) + surname.substring(1));
    }

    private List<Driver> invalidGrid(int sessionKey) {
        LOGGER.warn("OpenF1 starting grid for session {} is incomplete or invalid", sessionKey);
        return List.of();
    }
}
