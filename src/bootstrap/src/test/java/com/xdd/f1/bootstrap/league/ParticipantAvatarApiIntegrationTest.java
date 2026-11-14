package com.xdd.f1.bootstrap.league;

import com.xdd.f1.application.league.LeagueParticipantService;
import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.bootstrap.auth.SessionCookie;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.contract.user.DiscordUserApi;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class ParticipantAvatarApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private LeagueService leagues;
    @Autowired
    private LeagueParticipantService participants;
    @Autowired
    private SessionService sessions;
    @MockitoBean
    private DiscordUserApi avatars;

    @Test
    void returnsOnlyRequestedLeagueDiscordParticipantsUsingTheirActualParticipationIds() throws Exception {
        var league = leagues.createManual("Avatars");
        var discordId = discordId();
        var participant = participants.ensureParticipant(league.id(),
                new UserIdentityId(UserIdentitySource.DISCORD, discordId), "Discord participant");
        participants.ensureParticipant(league.id(), new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString()), "Manual participant");
        var otherLeague = leagues.createManual("Other avatars");
        participants.ensureParticipant(otherLeague.id(), new UserIdentityId(UserIdentitySource.DISCORD, discordId()), "Other participant");
        var url = "https://cdn.discordapp.com/avatars/" + discordId + "/avatar.png?size=64";
        when(avatars.getAvatarUrls(anyCollection())).thenReturn(Map.of(discordId, url));

        mvc.perform(get("/api/v1/leagues/{leagueId}/participants/avatars", league.id().value())
                        .cookie(new Cookie(SessionCookie.NAME, sessions.create(participant.userId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].participantId").value(participant.id().value().toString()))
                .andExpect(jsonPath("$.data[0].avatarUrl").value(url))
                .andExpect(jsonPath("$.data[0].discordId").doesNotExist());

        verify(avatars).getAvatarUrls(argThat(ids -> List.copyOf(ids).equals(List.of(discordId))));
        verifyNoMoreInteractions(avatars);
    }

    @Test
    void rejectsAnotherLeagueBeforeContactingDiscord() throws Exception {
        var league = leagues.createManual("Member league");
        var participant = participants.ensureParticipant(league.id(),
                new UserIdentityId(UserIdentitySource.DISCORD, discordId()), "Participant");
        var otherLeague = leagues.createManual("Forbidden league");

        mvc.perform(get("/api/v1/leagues/{leagueId}/participants/avatars", otherLeague.id().value())
                        .cookie(new Cookie(SessionCookie.NAME, sessions.create(participant.userId()))))
                .andExpect(status().isNotFound());

        verifyNoInteractions(avatars);
    }

    @Test
    void keepsParticipantWithoutAvatarWhenDiscordCannotResolveUser() throws Exception {
        var league = leagues.createManual("Missing avatar");
        var participant = participants.ensureParticipant(league.id(),
                new UserIdentityId(UserIdentitySource.DISCORD, discordId()), "Participant");
        when(avatars.getAvatarUrls(anyCollection())).thenReturn(Map.of());

        mvc.perform(get("/api/v1/leagues/{leagueId}/participants/avatars", league.id().value())
                        .cookie(new Cookie(SessionCookie.NAME, sessions.create(participant.userId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].participantId").value(participant.id().value().toString()))
                .andExpect(jsonPath("$.data[0].avatarUrl").isEmpty());
    }

    @Test
    void requiresNativeSession() throws Exception {
        mvc.perform(get("/api/v1/leagues/{leagueId}/participants/avatars", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(avatars);
    }

    private String discordId() {
        return Long.toString(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
    }
}
