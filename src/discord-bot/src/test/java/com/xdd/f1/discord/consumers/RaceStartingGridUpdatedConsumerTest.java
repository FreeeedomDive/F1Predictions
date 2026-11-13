package com.xdd.f1.discord.consumers;

import com.xdd.f1.contract.league.LeagueApi;
import com.xdd.f1.contract.league.LeagueDto;
import com.xdd.f1.contract.league.LeagueSettingsDto;
import com.xdd.f1.contract.race.RaceStartingGridUpdatedEvent;
import com.xdd.f1.discord.notifications.RaceNotificationSender;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RaceStartingGridUpdatedConsumerTest {
    private LeagueApi leagues;
    private JDA jda;
    private RaceStartingGridUpdatedConsumer consumer;

    @BeforeEach
    void setUp() {
        leagues = mock(LeagueApi.class);
        jda = mock(JDA.class);
        consumer = new RaceStartingGridUpdatedConsumer(new RaceNotificationSender(leagues, jda));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void announcesPublishedOrUpdatedSprintGridOnlyInEligibleLeagueChannels(boolean firstPublication) {
        when(leagues.findDiscordLeagues()).thenReturn(List.of(
                league("101", true, 2023),
                league("102", false, 2023),
                league("103", true, 2027),
                league(null, true, 2023),
                league("", true, 2023)
        ));
        var channel = channel("101");
        var event = new RaceStartingGridUpdatedEvent(UUID.randomUUID(), "Brazilian Grand Prix", "SPRINT", 2026,
                List.of("Hulkenberg", "Camara", "Piastri"), firstPublication);

        consumer.onRaceStartingGridUpdated(event);

        var embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(channel).sendMessageEmbeds(embed.capture());
        assertThat(embed.getValue().getTitle()).isEqualTo(
                "Starting grid " + (firstPublication ? "published" : "updated") + " · Brazilian Grand Prix (Sprint) · 2026"
        );
        assertThat(embed.getValue().getDescription()).isEqualTo("1. Hulkenberg\n2. Camara\n3. Piastri");
        verify(jda).getChannelById(GuildMessageChannel.class, "101");
        verifyNoMoreInteractions(jda);
    }

    @Test
    void announcesTheMainRaceToEveryEligibleLeagueIncludingThoseWithSprintsDisabled() {
        when(leagues.findDiscordLeagues()).thenReturn(List.of(
                league("101", true, 2023), league("102", false, 2026), league("103", true, 2027)
        ));
        var first = channel("101");
        var second = channel("102");
        var event = new RaceStartingGridUpdatedEvent(UUID.randomUUID(), "Brazilian Grand Prix", "RACE", 2026,
                List.of("Camara", "Piastri"), true);

        consumer.onRaceStartingGridUpdated(event);

        var embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(first).sendMessageEmbeds(embed.capture());
        verify(second).sendMessageEmbeds(embed.capture());
        assertThat(embed.getAllValues()).allSatisfy(message -> {
            assertThat(message.getTitle()).isEqualTo("Starting grid published · Brazilian Grand Prix · 2026");
            assertThat(message.getDescription()).isEqualTo("1. Camara\n2. Piastri");
        });
        verify(jda).getChannelById(GuildMessageChannel.class, "101");
        verify(jda).getChannelById(GuildMessageChannel.class, "102");
        verifyNoMoreInteractions(jda);
    }

    private GuildMessageChannel channel(String id) {
        var channel = mock(GuildMessageChannel.class);
        when(jda.getChannelById(GuildMessageChannel.class, id)).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(mock(MessageCreateAction.class));
        return channel;
    }

    private LeagueDto league(String channel, boolean sprintsEnabled, int firstSeason) {
        return new LeagueDto(UUID.randomUUID(), "League", new LeagueSettingsDto(channel, sprintsEnabled, firstSeason), Instant.EPOCH);
    }
}
