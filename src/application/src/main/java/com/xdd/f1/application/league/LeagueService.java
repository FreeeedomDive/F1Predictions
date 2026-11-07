package com.xdd.f1.application.league;

import com.xdd.f1.domain.league.*;
import com.xdd.f1.domain.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class LeagueService {
    private final LeagueRepository repository;
    private final LeagueParticipantRepository participantRepository;
    private final Clock clock;

    public LeagueService(
            LeagueRepository repository,
            LeagueParticipantRepository participantRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.participantRepository = participantRepository;
        this.clock = clock;
    }

    @Transactional
    public League createManual(String name) {
        var now = clock.instant();
        var league = new League(
                new LeagueId(UUID.randomUUID()),
                name,
                new LeagueSource.Manual(),
                new LeagueSettings(null, false, now.atZone(ZoneOffset.UTC).getYear()),
                now
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

        var now = clock.instant();
        var league = new League(
                new LeagueId(UUID.randomUUID()),
                name,
                new LeagueSource.Discord(guildId),
                new LeagueSettings(null, false, now.atZone(ZoneOffset.UTC).getYear()),
                now
        );
        repository.create(league);
        return league;
    }

    @Transactional(readOnly = true)
    public List<UserLeague> findLeaguesByUserId(UserId userId) {
        return participantRepository.findLeaguesByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<League> findAllDiscordLeagues() {
        var leagues = repository.findAll();
        return leagues.stream()
                .filter(league -> league.source() instanceof LeagueSource.Discord)
                .toList();
    }

    @Transactional
    public void updateNotificationsChannel(LeagueId id, String channelId) {
        repository.updateNotificationsChannel(id, channelId);
    }
}
