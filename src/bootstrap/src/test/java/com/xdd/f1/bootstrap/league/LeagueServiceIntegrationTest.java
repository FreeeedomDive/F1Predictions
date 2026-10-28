package com.xdd.f1.bootstrap.league;

import com.xdd.f1.application.league.LeagueRepository;
import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.league.LeagueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueServiceIntegrationTest extends AbstractIntegrationTest {

    private final LeagueService service;
    private final LeagueRepository repository;

    @Autowired
    LeagueServiceIntegrationTest(
            LeagueService service,
            LeagueRepository repository
    ) {
        this.service = service;
        this.repository = repository;
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
        assertThat(loaded.createdAt()).isNotNull();
    }

    @Test
    void createsDiscordLeagueAndFindsItByGuildId() {
        var guildId = 1L;
        var name = "Test server";

        var created = service.ensureDiscordLeague(guildId, name);

        var loaded = repository.findByDiscordId(guildId).orElseThrow();

        assertThat(loaded.id()).isEqualTo(created.id());
        assertThat(loaded.name()).isEqualTo(name);
        assertThat(loaded.source())
                .isEqualTo(new LeagueSource.Discord(guildId));
        assertThat(loaded.settings()).isNotNull();
        assertThat(loaded.settings().notificationsChannelId()).isNull();
    }

    @Test
    void returnsExistingDiscordLeagueOnRepeatedCall() {
        var guildId = 2L;
        var name = "Existing server";

        var first = service.ensureDiscordLeague(guildId, name);
        var second = service.ensureDiscordLeague(guildId, name);

        assertThat(second.id()).isEqualTo(first.id());

        var loaded = repository.findByDiscordId(guildId).orElseThrow();

        assertThat(loaded.id()).isEqualTo(first.id());
    }
}