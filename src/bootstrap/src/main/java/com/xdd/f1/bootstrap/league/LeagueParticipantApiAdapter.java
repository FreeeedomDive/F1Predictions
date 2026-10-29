package com.xdd.f1.bootstrap.league;

import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.contract.league.LeagueParticipantApi;
import com.xdd.f1.contract.league.LeagueParticipantDto;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LeagueParticipantApiAdapter implements LeagueParticipantApi {

    private final LeagueParticipantService service;

    public LeagueParticipantApiAdapter(LeagueParticipantService service) {
        this.service = service;
    }

    @Override
    public LeagueParticipantDto ensureDiscordParticipant(UUID leagueId, long discordUserId, String displayName) {
        var participant = service.ensureParticipant(new LeagueId(leagueId), new UserIdentityId(UserIdentitySource.DISCORD, String.valueOf(discordUserId)), displayName);
        return toDto(participant);
    }

    private LeagueParticipantDto toDto(LeagueParticipant participant) {
        return new LeagueParticipantDto(
                participant.id().value(),
                participant.leagueId().value(),
                participant.userId().value(),
                participant.displayName(),
                participant.joinedAt()
        );
    }
}
