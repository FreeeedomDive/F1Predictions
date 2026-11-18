package com.xdd.f1.discord.commands.standings;

import com.xdd.f1.contract.league.LeagueApi;
import com.xdd.f1.contract.league.LeagueDto;
import com.xdd.f1.contract.league.LeagueSettingsDto;
import com.xdd.f1.contract.prediction.PredictionPointsDto;
import com.xdd.f1.contract.race.RaceDto;
import com.xdd.f1.contract.race.RaceStatusDto;
import com.xdd.f1.contract.race.RaceTypeDto;
import com.xdd.f1.contract.standings.SeasonRacePointsDto;
import com.xdd.f1.contract.standings.SeasonStandingsDto;
import com.xdd.f1.contract.standings.SeasonStandingsRowDto;
import com.xdd.f1.contract.standings.StandingsApi;
import com.xdd.f1.discord.commands.core.CommandContext;
import com.xdd.f1.discord.configuration.WebAppProperties;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StandingsCommandTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    private final StandingsApi standings = mock(StandingsApi.class);
    private final LeagueApi leagues = mock(LeagueApi.class);
    private final ExecutorService executor = mock(ExecutorService.class);
    private final SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    private final Member member = mock(Member.class);
    private final LeagueDto league = new LeagueDto(UUID.randomUUID(), "League",
            new LeagueSettingsDto(null, false, 2023), Instant.EPOCH);

    private StandingsCommand command;
    private CommandContext context;

    @BeforeEach
    void setUp() {
        command = new StandingsCommand(standings, new WebAppProperties("https://f1.example/"), leagues, executor,
                Clock.fixed(NOW, ZoneOffset.UTC));
        context = new CommandContext(event, league, member);
    }

    @Test
    void registersPublicStandingsWithOptionalIntegerSeason() {
        var definition = command.definition();

        assertThat(definition.path()).isEqualTo("f1 standings");
        assertThat(command.ephemeral()).isFalse();
        assertThat(definition.subcommand().getOptions()).singleElement().satisfies(option -> {
            assertThat(option.getName()).isEqualTo("season");
            assertThat(option.getType()).isEqualTo(OptionType.INTEGER);
            assertThat(option.isRequired()).isFalse();
        });
    }

    @Test
    void usesServerOrderTotalsAndMaximumCountsAndAlignsMissingAndZeroPointsByEventId() {
        var race = race(RaceTypeDto.RACE);
        var sprint = race(RaceTypeDto.SPRINT);
        var leader = row("Leader", 888, 7, List.of(
                new SeasonRacePointsDto(sprint.id(), points(7)),
                new SeasonRacePointsDto(race.id(), null)
        ));
        var runnerUp = row("RunnerUp", 999, 1, List.of(new SeasonRacePointsDto(race.id(), points(0))));
        when(standings.getStandings(league.id(), 2026)).thenReturn(table(2026, List.of(race, sprint), List.of(leader, runnerUp)));

        var messages = command.executeWithResponses(context);

        assertThat(messages).singleElement().asString()
                .startsWith("Full standings: <https://f1.example/standings>\nRules: <https://f1.example/rulebook>\n```\n")
                .contains("1 | Leader   |     7 | 888 | 7x25\n", "2 | RunnerUp |  0    | 999 | 1x25\n")
                .endsWith("```");
        verify(standings).getStandings(league.id(), 2026);
    }

    @Test
    void usesExplicitSeasonAndReportsAnEmptySeason() {
        var option = mock(OptionMapping.class);
        when(event.getOption("season")).thenReturn(option);
        when(option.getAsInt()).thenReturn(2025);
        when(standings.getStandings(league.id(), 2025)).thenReturn(table(2025, List.of(), List.of()));

        assertThat(command.validateInput(context)).isEmpty();
        assertThat(command.executeWithResponses(context))
                .containsExactly("There are no completed predictions in the 2025 season yet.");
        verify(standings).getStandings(league.id(), 2025);
    }

    @Test
    void rejectsSeasonsBeforeTheLeagueStarted() {
        var option = mock(OptionMapping.class);
        when(event.getOption("season")).thenReturn(option);
        when(option.getAsInt()).thenReturn(2022);

        assertThat(command.validateInput(context)).isEqualTo("Predictions in this league start in 2023.");
        verifyNoInteractions(standings);
    }

    @Test
    void keepsNamesOnOneTableRowWithoutBreakingCodeBlocksOrUnicode() {
        var race = race(RaceTypeDto.RACE);
        var rows = List.of(row("Nick```\n\r\tName", 0, 0, List.of()),
                row("😀".repeat(40), 0, 0, List.of()));
        when(standings.getStandings(league.id(), 2026)).thenReturn(table(2026, List.of(race), rows));

        var response = command.executeWithResponse(context);

        assertThat(response).contains("Nick'''   Name", "😀".repeat(32)).doesNotContain("😀".repeat(33));
        assertThat(response.lines().filter(line -> line.equals("```"))).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsLargeStandingsPubliclyInOrderedMessagesWithinDiscordLimit() {
        var events = IntStream.range(0, 30).mapToObj(index -> race(index % 4 == 0 ? RaceTypeDto.SPRINT : RaceTypeDto.RACE)).toList();
        var results = events.stream().map(race -> new SeasonRacePointsDto(race.id(), points(25))).toList();
        var rows = IntStream.range(0, 100).mapToObj(index -> row("Participant" + index, 750, 30, results)).toList();
        when(standings.getStandings(league.id(), 2026)).thenReturn(table(2026, events, rows));
        var guild = mock(Guild.class);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(guild.getIdLong()).thenReturn(123L);
        when(guild.getName()).thenReturn("League");
        when(leagues.ensureDiscordLeague(123L, "League")).thenReturn(league);
        var acknowledgement = mock(ReplyCallbackAction.class);
        var hook = mock(InteractionHook.class);
        var edit = (WebhookMessageEditAction<Message>) mock(WebhookMessageEditAction.class);
        var followUp = (WebhookMessageCreateAction<Message>) mock(WebhookMessageCreateAction.class);
        when(event.deferReply(false)).thenReturn(acknowledgement);
        when(hook.editOriginal(anyString())).thenReturn(edit);
        when(edit.submit()).thenReturn(CompletableFuture.completedFuture(mock(Message.class)));
        when(hook.sendMessage(anyString())).thenReturn(followUp);
        when(followUp.setEphemeral(false)).thenReturn(followUp);
        when(followUp.submit()).thenReturn(CompletableFuture.completedFuture(mock(Message.class)));
        doAnswer(invocation -> {
            Consumer<InteractionHook> callback = invocation.getArgument(0);
            callback.accept(hook);
            return null;
        }).when(acknowledgement).queue(any(), any());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        command.execute(event);

        verify(event).deferReply(false);
        verify(event, never()).deferReply(true);
        var original = ArgumentCaptor.forClass(String.class);
        var subsequent = ArgumentCaptor.forClass(String.class);
        verify(hook).editOriginal(original.capture());
        verify(hook, atLeastOnce()).sendMessage(subsequent.capture());
        verify(followUp, atLeastOnce()).setEphemeral(false);
        var messages = new ArrayList<String>();
        messages.add(original.getValue());
        messages.addAll(subsequent.getAllValues());
        assertThat(messages).hasSizeGreaterThan(1).allSatisfy(message -> {
            assertThat(message.length()).isLessThanOrEqualTo(Message.MAX_CONTENT_LENGTH);
            assertThat(message.lines().filter(line -> line.equals("```"))).hasSize(2);
        });
        var content = String.join("\n", messages);
        for (int index = 0; index < rows.size(); index++) {
            assertThat(content).contains((index + 1) + " | Participant" + index + " ");
        }
        assertThat(content.indexOf("1 | Participant0 ")).isLessThan(content.indexOf("100 | Participant99 "));
    }

    private RaceDto race(RaceTypeDto type) {
        return new RaceDto(UUID.randomUUID(), "Grand Prix", type, RaceStatusDto.FINISHED, NOW.minusSeconds(3600), NOW);
    }

    private PredictionPointsDto points(int total) {
        return new PredictionPointsDto("2026-v1", total, null, null, null, null, null, null);
    }

    private SeasonStandingsRowDto row(String name, int total, int maximumCount, List<SeasonRacePointsDto> results) {
        return new SeasonStandingsRowDto(UUID.randomUUID(), name, total, maximumCount, results, List.of());
    }

    private SeasonStandingsDto table(int season, List<RaceDto> events, List<SeasonStandingsRowDto> rows) {
        return new SeasonStandingsDto(season, events, rows, rows.isEmpty() ? null : rows.getFirst().participantId(),
                rows.isEmpty() ? 0 : rows.getFirst().totalPoints(), 0, List.of(), List.of());
    }
}
