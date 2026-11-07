package com.xdd.f1.bootstrap.league;

import com.xdd.f1.application.league.LeagueNotFoundException;
import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.when;

class LeagueServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-12-31T23:59:59Z");

    private final LeagueService service;
    private final LeagueRepository repository;

    @MockitoBean
    private Clock clock;

    @Autowired
    LeagueServiceIntegrationTest(
            LeagueService service,
            LeagueRepository repository
    ) {
        this.service = service;
        this.repository = repository;
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void createsManualLeagueWithSettings() {
        var name = "Test";
        var created = service.createManual(name);

        var loaded = repository.findById(created.id()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(created.id());
        assertThat(loaded.name()).isEqualTo(name);
        assertThat(loaded.source()).isEqualTo(new LeagueSource.Manual());
        assertThat(loaded.settings()).isNotNull();
        assertThat(loaded.settings().notificationsChannelId()).isNull();
        assertThat(loaded.settings().sprintsEnabled()).isFalse();
        assertThat(loaded.settings().firstSeason()).isEqualTo(2026);
        assertThat(loaded.createdAt()).isEqualTo(NOW);
    }

    @Test
    void createsDiscordLeagueAndFindsItByGuildId() {
        var guildId = 1L;
        var name = "Test server 1";

        var created = service.ensureDiscordLeague(guildId, name);

        var loaded = repository.findByDiscordId(guildId).orElseThrow();

        assertThat(loaded.id()).isEqualTo(created.id());
        assertThat(loaded.name()).isEqualTo(name);
        assertThat(loaded.source())
                .isEqualTo(new LeagueSource.Discord(guildId));
        assertThat(loaded.settings()).isNotNull();
        assertThat(loaded.settings().notificationsChannelId()).isNull();
        assertThat(loaded.settings().sprintsEnabled()).isFalse();
        assertThat(loaded.settings().firstSeason()).isEqualTo(2026);
    }

    @Test
    void keepsFirstSeasonWhenDiscordLeagueIsRediscoveredNextYear() {
        var guildId = 2L;
        var name = "Existing server 2";

        var first = service.ensureDiscordLeague(guildId, name);
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        var second = service.ensureDiscordLeague(guildId, name);
        var newLeague = service.ensureDiscordLeague(6L, "New year league");

        assertThat(second.id()).isEqualTo(first.id());

        var loaded = repository.findByDiscordId(guildId).orElseThrow();

        assertThat(loaded.id()).isEqualTo(first.id());
        assertThat(loaded.settings().firstSeason()).isEqualTo(2026);
        assertThat(second.settings().firstSeason()).isEqualTo(2026);
        assertThat(newLeague.settings().firstSeason()).isEqualTo(2027);
    }

    @Test
    void preservesImportedFirstSeasonWhenUpdatingNotifications() {
        var league = new League(
                new LeagueId(UUID.randomUUID()),
                "Imported league",
                new LeagueSource.Discord(5L),
                new LeagueSettings(null, false, 2023),
                NOW
        );
        repository.create(league);

        service.updateNotificationsChannel(league.id(), "imported-channel");

        var loaded = repository.findById(league.id()).orElseThrow();
        assertThat(loaded.settings().firstSeason()).isEqualTo(2023);
        assertThat(loaded.settings().notificationsChannelId()).isEqualTo("imported-channel");
    }

    @Test
    void updatesNotificationsChannel() {
        var guildId = 3L;
        var name = "Server 3";
        var league = service.ensureDiscordLeague(guildId, name);
        var channelId = "some-channel-id";

        service.updateNotificationsChannel(league.id(), channelId);

        var loaded = repository.findByDiscordId(guildId).orElseThrow();
        assertThat(loaded.settings().notificationsChannelId()).isEqualTo(channelId);
    }

    @Test
    void erasesNotificationsChannel() {
        var guildId = 4L;
        var name = "Server 4";
        var league = service.ensureDiscordLeague(guildId, name);
        var channelId = "some-channel-id";
        service.updateNotificationsChannel(league.id(), channelId);

        service.updateNotificationsChannel(league.id(), null);

        var loaded = repository.findByDiscordId(guildId).orElseThrow();
        assertThat(loaded.settings().notificationsChannelId()).isNull();
        assertThat(loaded.settings().sprintsEnabled()).isFalse();
    }

    @Test
    void throwsOnUpdatingNotificationsChannelForNonExistingLeague() {
        var channelId = "some-channel-id";

        assertThatThrownBy(() -> service.updateNotificationsChannel(new LeagueId(UUID.randomUUID()), channelId))
                .isInstanceOf(LeagueNotFoundException.class);
    }
}
