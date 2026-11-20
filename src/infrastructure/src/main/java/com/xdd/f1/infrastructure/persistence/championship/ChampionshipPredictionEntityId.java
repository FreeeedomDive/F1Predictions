package com.xdd.f1.infrastructure.persistence.championship;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record ChampionshipPredictionEntityId(
        @Column(name = "league_id", nullable = false)
        UUID leagueId,
        @Column(name = "season", nullable = false)
        int season,
        @Column(name = "participant_id", nullable = false)
        UUID participantId,
        @Column(name = "stage", nullable = false)
        String stage
) {
}
