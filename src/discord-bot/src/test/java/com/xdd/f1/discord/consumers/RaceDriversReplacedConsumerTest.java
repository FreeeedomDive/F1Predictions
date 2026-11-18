package com.xdd.f1.discord.consumers;

import com.xdd.f1.contract.race.RaceDriversMovedEvent;
import com.xdd.f1.discord.notifications.RaceNotificationSender;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RaceDriversReplacedConsumerTest {
    @ParameterizedTest
    @MethodSource("moves")
    void showsDeparturesAndArrivalsOnEitherSideOfAnArrow(
            List<String> departures, List<String> arrivals, String expected
    ) {
        var sender = mock(RaceNotificationSender.class);
        var consumer = new RaceDriversReplacedConsumer(sender);
        consumer.onRaceDriversMoved(new RaceDriversMovedEvent(
                UUID.randomUUID(), "Las Vegas Grand Prix", "RACE", "Red Bull", "#1F00B8", departures, arrivals
        ));

        var embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(sender).sendToLeagues(eq("RACE"), embed.capture());
        assertThat(embed.getValue().getFields()).singleElement().satisfies(field -> {
            assertThat(field.getName()).isEqualTo("Driver moves");
            assertThat(field.getValue()).isEqualTo("Las Vegas Grand Prix — Red Bull\n" + expected);
        });
    }

    private static Stream<Arguments> moves() {
        return Stream.of(
                Arguments.of(List.of("Lawson"), List.of("Hadjar"), "Lawson → Hadjar"),
                Arguments.of(List.of("Lawson"), List.of(), "Lawson → —"),
                Arguments.of(List.of(), List.of("Hadjar"), "— → Hadjar"),
                Arguments.of(List.of("Lawson", "Verstappen"), List.of("Hadjar"), "Lawson, Verstappen → Hadjar")
        );
    }
}
