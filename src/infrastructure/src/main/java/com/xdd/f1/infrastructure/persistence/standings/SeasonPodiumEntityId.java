package com.xdd.f1.infrastructure.persistence.standings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record SeasonPodiumEntityId(
    @Column(name = "league_id", nullable = false)
    UUID leagueId,

    @Column(name = "season", nullable = false)
    int season
)
{}
