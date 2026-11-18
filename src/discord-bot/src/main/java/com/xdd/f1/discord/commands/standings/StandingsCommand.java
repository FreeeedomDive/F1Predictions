package com.xdd.f1.discord.commands.standings;

import com.xdd.f1.contract.league.LeagueApi;
import com.xdd.f1.contract.standings.SeasonRacePointsDto;
import com.xdd.f1.contract.standings.StandingsApi;
import com.xdd.f1.discord.commands.core.*;
import com.xdd.f1.discord.configuration.WebAppProperties;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@DiscordComponent
public class StandingsCommand extends AbstractCommand {
    private static final Logger log = LoggerFactory.getLogger(StandingsCommand.class);

    private final StandingsApi standings;
    private final WebAppProperties web;
    private final Clock clock;

    protected StandingsCommand(StandingsApi standings, WebAppProperties web, LeagueApi leagues,
                               ExecutorService executor, Clock clock) {
        super(leagues, executor, log);
        this.standings = standings;
        this.web = web;
        this.clock = clock;
    }

    @Override
    protected CommandDefinition definition() {
        var season = new OptionData(OptionType.INTEGER, "season", "Season (current year by default)", false)
                .setMinValue(1).setMaxValue(9999);
        return new CommandDefinition(CommandRoot.F1,
                new SubcommandData("standings", "Show the prediction standings").addOptions(season));
    }

    @Override
    protected boolean ephemeral() {
        return false;
    }

    @Override
    protected String validateInput(CommandContext context) {
        return season(context) < context.league().settings().firstSeason()
                ? "Predictions in this league start in " + context.league().settings().firstSeason() + "."
                : "";
    }

    @Override
    protected String executeWithResponse(CommandContext context) {
        var season = season(context);
        var table = standings.getStandings(context.league().id(), season);
        if (table.rows().isEmpty() || table.events().isEmpty()) {
            return "There are no completed predictions in the " + season + " season yet.";
        }

        var names = table.rows().stream().map(row -> name(row.participantName())).toList();
        var nameWidth = names.stream().mapToInt(String::length).max().orElse(0);
        var message = new StringBuilder(links()).append("```\n");
        for (int index = 0; index < table.rows().size(); index++) {
            var row = table.rows().get(index);
            var results = row.results().stream().filter(result -> result.points() != null)
                    .collect(Collectors.toMap(SeasonRacePointsDto::raceId, SeasonRacePointsDto::points));
            var eventPoints = table.events().stream().map(event -> {
                var points = results.get(event.id());
                return points == null ? "  " : String.format("%2d", points.totalPoints());
            }).collect(Collectors.joining(" "));
            message.append(index + 1).append(" | ")
                    .append(String.format("%-" + nameWidth + "s", names.get(index))).append(" | ")
                    .append(eventPoints).append(" | ")
                    .append(String.format("%3d", row.totalPoints())).append(" | ")
                    .append(row.maximumTenthPlaceCount()).append("x25\n");
        }
        return message.append("```").toString();
    }

    @Override
    protected List<String> executeWithResponses(CommandContext context) {
        var response = executeWithResponse(context);
        if (response.length() <= Message.MAX_CONTENT_LENGTH) {
            return List.of(response);
        }

        var tableStart = response.indexOf("```\n") + 4;
        var messages = new ArrayList<String>();
        var current = new StringBuilder(response.substring(0, tableStart));
        for (var line : response.substring(tableStart, response.length() - 3).lines().toList()) {
            if (line.length() + 8 > Message.MAX_CONTENT_LENGTH) {
                return List.of(links() + "The table is too wide to display in Discord. Open the full standings above.");
            }
            if (current.length() + line.length() + 4 > Message.MAX_CONTENT_LENGTH) {
                messages.add(current.append("```").toString());
                current = new StringBuilder("```\n");
            }
            current.append(line).append('\n');
        }
        messages.add(current.append("```").toString());
        return List.copyOf(messages);
    }

    private int season(CommandContext context) {
        var option = context.event().getOption("season");
        return option == null ? Year.now(clock).getValue() : option.getAsInt();
    }

    private String links() {
        var standingsUrl = UriComponentsBuilder.fromUriString(web.appBaseUrl())
                .pathSegment("standings").build().toUriString();
        var rulesUrl = UriComponentsBuilder.fromUriString(web.appBaseUrl())
                .pathSegment("rulebook").build().toUriString();
        return "Full standings: <" + standingsUrl + ">\nRules: <" + rulesUrl + ">\n";
    }

    private String name(String value) {
        var text = value.replace('`', '\'').replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').strip();
        var end = text.offsetByCodePoints(0, Math.min(text.codePointCount(0, text.length()), 32));
        return text.substring(0, end);
    }
}
