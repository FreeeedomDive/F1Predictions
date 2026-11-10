package com.xdd.f1.discord.notifications;

import com.xdd.f1.contract.race.RaceFinishedEvent;
import com.xdd.f1.contract.race.RaceResultUpdatedEvent;
import com.xdd.f1.contract.race.result.RaceDriverStatusDto;
import com.xdd.f1.contract.race.result.RaceParticipantPointsDto;
import com.xdd.f1.contract.race.result.RaceResultValuesDto;
import com.xdd.f1.discord.configuration.WebAppProperties;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
@ConditionalOnProperty(prefix = "discord", name = "enabled", havingValue = "true")
public class RaceResultEmbedBuilder {
    private final WebAppProperties webAppProperties;

    public RaceResultEmbedBuilder(WebAppProperties webAppProperties) {
        this.webAppProperties = webAppProperties;
    }

    public List<MessageEmbed> build(RaceFinishedEvent event) {
        return build(event.raceId(), event.raceName(), event.raceType(), event.season(), event.rulesVersion(),
                event.result(), event.points(), false);
    }

    public List<MessageEmbed> build(RaceResultUpdatedEvent event) {
        return build(event.raceId(), event.raceName(), event.raceType(), event.season(), event.rulesVersion(),
                event.result(), event.points(), true);
    }

    private List<MessageEmbed> build(UUID raceId, String raceName, String raceType, int season, String rulesVersion,
                                      RaceResultValuesDto result, List<RaceParticipantPointsDto> points, boolean updated) {
        var title = (updated ? "Race results updated" : "Race finished") + " · " + name(raceName)
                + (raceType.equals("SPRINT") ? " (Sprint)" : "") + " · " + season;
        var url = UriComponentsBuilder.fromUriString(webAppProperties.appBaseUrl())
                .pathSegment("races", raceId.toString()).build().toUriString();
        var lines = new ArrayList<String>();
        lines.add(updated ? "Race results have been updated and prediction points recalculated." : "Prediction points have been calculated.");
        if (result.classification() != null && !result.classification().isEmpty()) {
            lines.add("");
            lines.add("**Classification**");
            for (int index = 0; index < result.classification().size(); index++) {
                var entry = result.classification().get(index);
                var status = entry.status() == RaceDriverStatusDto.FINISHED ? "" : " · " + entry.status().name();
                lines.add((index + 1) + ". " + name(entry.driverName()) + status);
            }
        }
        if (result.safetyCars() != null || result.firstPlaceLead() != null) {
            lines.add("");
        }
        if (result.safetyCars() != null) {
            lines.add("Incidents: **" + result.safetyCars() + "**");
        }
        if (result.firstPlaceLead() != null) {
            lines.add("Winner's gap: **" + result.firstPlaceLead().stripTrailingZeros().toPlainString() + " s**");
        }
        lines.add("");
        lines.add("**Prediction points**");
        for (var participant : points) {
            lines.add("• " + name(participant.participantName()) + " — **" + participant.totalPoints() + "** "
                    + (participant.totalPoints() == 1 ? "point" : "points"));
        }
        if (points.isEmpty()) {
            lines.add("No predictions.");
        }
        var pages = pages(lines);
        return IntStream.range(0, pages.size()).mapToObj(index -> new EmbedBuilder()
                .setColor(updated ? new Color(0x006400) : new Color(0xFFD700))
                .setTitle(title, url)
                .setDescription(pages.get(index))
                .setFooter("Rules: " + label(rulesVersion, 128)
                        + (pages.size() > 1 ? " · Page " + (index + 1) + " of " + pages.size() : ""))
                .build()).toList();
    }

    private List<String> pages(List<String> lines) {
        var pages = new ArrayList<String>();
        var page = new StringBuilder();
        for (var line : lines) {
            if (page.length() + line.length() + 1 > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
                pages.add(page.toString());
                page.setLength(0);
            }
            if (!page.isEmpty()) {
                page.append('\n');
            }
            page.append(line);
        }
        if (!page.isEmpty()) {
            pages.add(page.toString());
        }
        return pages;
    }

    private String name(String value) {
        return MarkdownSanitizer.escape(label(value, 100));
    }

    private String label(String value, int limit) {
        var text = value.replace('\n', ' ').replace('\r', ' ').strip();
        if (text.length() <= limit) {
            return text;
        }
        var end = limit - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "…";
    }
}
