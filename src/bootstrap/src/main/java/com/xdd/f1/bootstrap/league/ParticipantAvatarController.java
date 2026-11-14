package com.xdd.f1.bootstrap.league;

import com.xdd.f1.application.league.LeagueAccessGuard;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.common.ListResponseDto;
import com.xdd.f1.contract.league.ParticipantAvatarDto;
import com.xdd.f1.contract.user.DiscordUserApi;
import com.xdd.f1.domain.league.LeagueId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leagues/{leagueId}/participants/avatars")
public class ParticipantAvatarController {
    private final LeagueAccessGuard access;
    private final LeagueParticipantRepository participants;
    private final ObjectProvider<DiscordUserApi> discordUsers;

    public ParticipantAvatarController(LeagueAccessGuard access, LeagueParticipantRepository participants,
                                       ObjectProvider<DiscordUserApi> discordUsers) {
        this.access = access;
        this.participants = participants;
        this.discordUsers = discordUsers;
    }

    @GetMapping
    public ResponseEntity<ListResponseDto<ParticipantAvatarDto>> getAvatars(@CurrentUser AuthenticatedUser user,
                                                                          @PathVariable UUID leagueId) {
        var league = new LeagueId(leagueId);
        access.checkAccess(user.id(), league);
        var identities = participants.findDiscordIdsByLeague(league);
        var users = discordUsers.getIfAvailable();
        var avatars = users == null ? Map.<String, String>of() : users.getAvatarUrls(identities.values());
        var data = identities.entrySet().stream()
                .map(entry -> new ParticipantAvatarDto(entry.getKey().value(), avatars.get(entry.getValue())))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(new ListResponseDto<>(data));
    }
}
