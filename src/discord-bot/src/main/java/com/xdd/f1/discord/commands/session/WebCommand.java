package com.xdd.f1.discord.commands.session;

import com.xdd.f1.contract.league.LeagueApi;
import com.xdd.f1.contract.league.LeagueParticipantApi;
import com.xdd.f1.contract.session.SessionApi;
import com.xdd.f1.discord.commands.core.*;
import com.xdd.f1.discord.configuration.WebAppProperties;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.ExecutorService;

@DiscordComponent
public class WebCommand extends AbstractCommand {
    private final LeagueParticipantApi leagueParticipants;
    private final SessionApi sessions;
    private final WebAppProperties webAppProperties;
    private static final Logger log = LoggerFactory.getLogger(WebCommand.class);

    protected WebCommand(
            LeagueParticipantApi leagueParticipants,
            SessionApi sessions,
            WebAppProperties webAppProperties,
            LeagueApi leagues,
            ExecutorService executor
    ) {
        super(leagues, executor, log);
        this.leagueParticipants = leagueParticipants;
        this.sessions = sessions;
        this.webAppProperties = webAppProperties;
    }

    @Override
    protected CommandDefinition definition() {
        var subcommand = new SubcommandData(
                "web",
                "Login to web application"
        );

        return new CommandDefinition(CommandRoot.F1, subcommand);
    }

    @Override
    protected String validateInput(CommandContext context) {
        return "";
    }

    @Override
    protected String executeWithResponse(CommandContext context) {
        var participant = leagueParticipants.ensureDiscordParticipant(
                context.league().id(),
                context.member().getIdLong(),
                context.member().getEffectiveName()
        );
        var token = sessions.create(participant.userId());
        var url = webAppProperties.appBaseUrl();
        return UriComponentsBuilder.fromUriString(url)
                .path("/auth")
                .queryParam("token", "{token}").encode().buildAndExpand(token)
                .toUriString();
    }
}
