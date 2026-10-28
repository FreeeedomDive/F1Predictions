package com.xdd.f1.bootstrap.league;

import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.contract.league.LeagueApi;
import com.xdd.f1.contract.league.LeagueDto;
import com.xdd.f1.contract.league.LeagueSettingsDto;
import com.xdd.f1.domain.league.League;
import com.xdd.f1.domain.league.LeagueId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LeagueApiAdapter implements LeagueApi {

    private final LeagueService service;

    public LeagueApiAdapter(LeagueService service) {
        this.service = service;
    }

    @Override
    public LeagueDto ensureDiscordLeague(long guildId, String name) {
        var league = service.ensureDiscordLeague(guildId, name);
        return toDto(league);
    }

    @Override
    public void updateNotificationsChannel(UUID leagueId, String channelId) {
        service.updateNotificationsChannel(new LeagueId(leagueId), channelId);
    }

    private LeagueDto toDto(League league) {
        return new LeagueDto(
                league.id().value(),
                league.name(),
                new LeagueSettingsDto(
                        league.settings().notificationsChannelId()
                ),
                league.createdAt()
        );
    }
}
