package com.xdd.f1.discord.consumers;

import com.xdd.f1.contract.bingo.BingoAchievedEvent;
import com.xdd.f1.contract.league.LeagueApi;
import com.xdd.f1.contract.league.LeagueDto;
import com.xdd.f1.contract.league.LeagueSettingsDto;
import com.xdd.f1.discord.notifications.LeagueNotificationSender;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BingoAchievedConsumerTest {
    private LeagueApi leagues;
    private JDA jda;
    private BingoAchievedConsumer consumer;

    @BeforeEach
    void setUp() {
        leagues = mock(LeagueApi.class);
        jda = mock(JDA.class);
        consumer = new BingoAchievedConsumer(new LeagueNotificationSender(leagues, jda));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void announcesBingoOnlyInItsLeagueWithoutSeasonLinksOrSprintFiltering(boolean sprintsEnabled) {
        var target = league("101", sprintsEnabled);
        when(leagues.findDiscordLeagues()).thenReturn(List.of(league("102", true), target));
        var channel = mock(GuildMessageChannel.class);
        var message = mock(MessageCreateAction.class);
        when(jda.getChannelById(GuildMessageChannel.class, "101")).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(message);
        var event = event(target.id(), "Alice");

        consumer.onBingoAchieved(event);

        var embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(channel).sendMessageEmbeds(embed.capture());
        assertThat(embed.getValue().getTitle()).isEqualTo("Bingo!");
        assertThat(embed.getValue().getDescription()).isEqualTo("**Alice** has achieved bingo!");
        assertThat(embed.getValue().getUrl()).isNull();
        assertThat(embed.getValue().getFields()).isEmpty();
        assertThat(embed.getValue().getDescription()).doesNotContain(
                String.valueOf(event.season()), event.boardId().toString(), event.participantId().toString(), "http");
        verify(message).complete();
        verify(jda).getChannelById(GuildMessageChannel.class, "101");
        verifyNoMoreInteractions(jda);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void skipsLeaguesWithoutANotificationChannel(String channelId) {
        var target = league(channelId, false);
        when(leagues.findDiscordLeagues()).thenReturn(List.of(target));

        consumer.onBingoAchieved(event(target.id(), "Alice"));

        verifyNoInteractions(jda);
    }

    @Test
    void doesNotSendToAnotherLeagueWhenTheTargetIsNotADiscordLeague() {
        when(leagues.findDiscordLeagues()).thenReturn(List.of(league("101", true)));

        consumer.onBingoAchieved(event(UUID.randomUUID(), "Alice"));

        verifyNoInteractions(jda);
    }

    @Test
    void skipsAnUnavailableChannel() {
        var target = league("101", false);
        when(leagues.findDiscordLeagues()).thenReturn(List.of(target));

        consumer.onBingoAchieved(event(target.id(), "Alice"));

        verify(jda).getChannelById(GuildMessageChannel.class, "101");
        verifyNoMoreInteractions(jda);
    }

    @Test
    void propagatesDeliveryFailuresSoTheEventIsNotMarkedCompleted() {
        var target = league("101", false);
        when(leagues.findDiscordLeagues()).thenReturn(List.of(target));
        var channel = mock(GuildMessageChannel.class);
        var message = mock(MessageCreateAction.class);
        when(jda.getChannelById(GuildMessageChannel.class, "101")).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(message);
        var failure = new IllegalStateException("Discord unavailable");
        when(message.complete()).thenThrow(failure);

        assertThatThrownBy(() -> consumer.onBingoAchieved(event(target.id(), "Alice"))).isSameAs(failure);
    }

    @Test
    void escapesParticipantNamesAndKeepsTheAnnouncementOnOneLine() {
        var sender = mock(LeagueNotificationSender.class);
        var consumer = new BingoAchievedConsumer(sender);
        var event = event(UUID.randomUUID(), "  **Alice**\n[admin]\ruser  ");

        consumer.onBingoAchieved(event);

        var embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(sender).sendToLeague(eq(event.leagueId()), embed.capture());
        assertThat(embed.getValue().getDescription()).isEqualTo(
                "**" + MarkdownSanitizer.escape("**Alice** [admin] user") + "** has achieved bingo!");
    }

    @Test
    void boundsLongNamesWithoutSplittingUnicodeCharacters() {
        var sender = mock(LeagueNotificationSender.class);
        var consumer = new BingoAchievedConsumer(sender);
        var event = event(UUID.randomUUID(), "A".repeat(99) + "🏎️" + "B".repeat(5_000));

        consumer.onBingoAchieved(event);

        var embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(sender).sendToLeague(eq(event.leagueId()), embed.capture());
        assertThat(embed.getValue().getDescription()).isEqualTo("**" + "A".repeat(99) + "🏎** has achieved bingo!");
    }

    private LeagueDto league(String channelId, boolean sprintsEnabled) {
        return new LeagueDto(UUID.randomUUID(), "League",
                new LeagueSettingsDto(channelId, sprintsEnabled, 2023), Instant.EPOCH);
    }

    private BingoAchievedEvent event(UUID leagueId, String name) {
        return new BingoAchievedEvent(leagueId, 2026, UUID.randomUUID(), UUID.randomUUID(), name);
    }
}
