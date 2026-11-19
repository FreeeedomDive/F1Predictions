package com.xdd.f1.bootstrap.e2e;

import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.locking.TransactionLock;
import com.xdd.f1.application.race.RaceService;
import com.xdd.f1.application.race.lineup.RaceLineupRepository;
import com.xdd.f1.application.rights.RightsService;
import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
import com.xdd.f1.domain.race.Race;
import com.xdd.f1.domain.race.RaceType;
import com.xdd.f1.domain.race.lineup.Driver;
import com.xdd.f1.domain.race.lineup.RaceLineup;
import com.xdd.f1.domain.race.lineup.Team;
import com.xdd.f1.domain.race.lineup.TeamBranding;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

@Profile("e2e")
@RestController
public class E2eFixtureController {
    private static final int SEASON = 2026;
    private static final List<Team> TEAMS = IntStream.rangeClosed(1, 5)
            .mapToObj(i -> new Team(
                    new TeamBranding("Team " + i, "T" + i, "#64748B", "#FFFFFF"),
                    List.of(new Driver("Driver " + (i * 2 - 1)), new Driver("Driver " + (i * 2)))
            )).toList();
    private static final List<String> DRIVERS = TEAMS.stream()
            .flatMap(team -> team.drivers().stream()).map(Driver::name).toList();

    private final LeagueRepository leagues;
    private final LeagueParticipantService participants;
    private final RightsService rights;
    private final SessionService sessions;
    private final RaceService races;
    private final RaceLineupRepository lineups;
    private final TransactionLock lock;
    private final EntityManager entityManager;
    private final Clock clock;

    public E2eFixtureController(LeagueRepository leagues, LeagueParticipantService participants,
                                RightsService rights, SessionService sessions, RaceService races,
                                RaceLineupRepository lineups, TransactionLock lock, EntityManager entityManager,
                                Clock clock) {
        this.leagues = leagues;
        this.participants = participants;
        this.rights = rights;
        this.sessions = sessions;
        this.races = races;
        this.lineups = lineups;
        this.lock = lock;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @PostMapping("/e2e/fixtures")
    @Transactional
    public ResponseEntity<Fixture> create(@Valid @RequestBody FixtureRequest request) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var now = clock.instant();
        var leagueRefs = new ArrayList<LeagueRef>();
        for (var sprintsEnabled : request.sprintsEnabled()) {
            var id = new LeagueId(UUID.randomUUID());
            var name = "E2E League " + (leagueRefs.size() + 1) + " " + suffix;
            leagues.create(new League(id, name, new LeagueSource.Manual(),
                    new LeagueSettings(null, sprintsEnabled, SEASON), now));
            leagueRefs.add(new LeagueRef(id.value(), name));
        }

        var member = createUser("E2E Player " + suffix, leagueRefs, List.of());
        var admin = request.adminRights().isEmpty() ? null
                : createUser("E2E Admin " + suffix, leagueRefs.subList(0, 1), request.adminRights());
        var raceRefs = new ArrayList<RaceRef>();
        if (!request.raceTypes().isEmpty()) {
            lock.acquire("e2e:race-round");
            var round = races.findAllBySeason(SEASON).stream().mapToInt(Race::round).max().orElse(0);
            for (var type : request.raceTypes()) {
                var name = "E2E " + (type == RaceType.SPRINT ? "Sprint " : "Race ") + (++round);
                var race = races.create(SEASON, round, type, name, now.minus(Duration.ofHours(1)),
                        now.plus(Duration.ofDays(1)));
                entityManager.flush();
                if (!lineups.createIfAbsent(new RaceLineup(race.id(), TEAMS))) {
                    throw new IllegalStateException("E2E race lineup already exists");
                }
                raceRefs.add(new RaceRef(race.id().value(), name));
            }
        }
        var fixture = new Fixture(SEASON, member, admin, leagueRefs, raceRefs, DRIVERS);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(fixture);
    }

    private UserRef createUser(String name, List<LeagueRef> leagues, List<Right> initialRights) {
        var identity = new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString());
        UserId userId = null;
        for (var league : leagues) {
            userId = participants.ensureParticipant(new LeagueId(league.id()), identity, name).userId();
        }
        var id = Objects.requireNonNull(userId);
        initialRights.forEach(right -> rights.grant(id, right));
        return new UserRef(name, sessions.create(id));
    }

    public record FixtureRequest(@NotEmpty List<@NotNull Boolean> sprintsEnabled,
                                 @NotNull List<@NotNull RaceType> raceTypes,
                                 @NotNull List<@NotNull Right> adminRights) {}
    public record Fixture(int season, UserRef member, UserRef admin, List<LeagueRef> leagues,
                          List<RaceRef> races, List<String> drivers) {}
    public record UserRef(String name, String token) {}
    public record LeagueRef(UUID id, String name) {}
    public record RaceRef(UUID id, String name) {}
}
