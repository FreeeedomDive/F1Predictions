package com.xdd.f1.bootstrap.user;

import com.xdd.f1.application.league.LeagueService;
import com.xdd.f1.application.league.UserLeague;
import com.xdd.f1.bootstrap.auth.AuthenticatedUser;
import com.xdd.f1.bootstrap.auth.CurrentUser;
import com.xdd.f1.contract.user.MeResponseDto;
import com.xdd.f1.contract.user.UserLeagueDto;
import com.xdd.f1.domain.user.UserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final LeagueService leagueService;

    public MeController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }

    @GetMapping
    public MeResponseDto me(@CurrentUser AuthenticatedUser user) {
        var leagues = leagueService.findLeaguesByUserId(user.id()).stream().map(this::toDto).toList();
        return new MeResponseDto(user.id().value(), leagues);
    }

    private UserLeagueDto toDto(UserLeague userLeague) {
        return new UserLeagueDto(
                userLeague.leagueId().value(),
                userLeague.participantId().value(),
                userLeague.leagueName(),
                userLeague.participantName()
        );
    }
}
