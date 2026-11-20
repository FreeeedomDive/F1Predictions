package com.xdd.f1.application.championship;

import com.xdd.f1.domain.championship.ChampionshipPrediction;

import java.util.List;

public record ChampionshipScoredPrediction(ChampionshipPrediction prediction, List<Integer> points) {
}
