package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueSettings;
import com.xdd.f1.domain.league.LeagueSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class LeagueService {
    private final LeagueRepository repository;

    public LeagueService(LeagueRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public League createManual(String name) {
        var league = new League(
                new LeagueId(UUID.randomUUID()),
                name,
                new LeagueSource.Manual(),
                new LeagueSettings(null),
                Instant.now()
        );
        repository.create(league);
        return league;
    }

    @Transactional
    public League ensureDiscordLeague(long guildId, String name) {
        var existing = repository.findByDiscordId(guildId);
        if (existing.isPresent()) {
            return existing.get();
        }

        var league = new League(
                new LeagueId(UUID.randomUUID()),
                name,
                new LeagueSource.Discord(guildId),
                new LeagueSettings(null),
                Instant.now()
        );
        repository.create(league);
        return league;
    }
}
