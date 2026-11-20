package com.xdd.f1.domain.championship;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public final class ChampionshipPointsCalculator {
    private static final int[] PRE_SEASON_POINTS = {20, 14, 11, 8, 5, 2};
    private static final int[] MID_SEASON_POINTS = {10, 6, 4, 2, 1};

    private ChampionshipPointsCalculator() {
    }

    public static List<Integer> calculate(ChampionshipSeason season, ChampionshipStage stage,
                                          List<String> driverStandings, List<String> orderedDrivers) {
        if (!season.isPublished(stage) || driverStandings.isEmpty()) {
            return List.of();
        }

        var scoringStandings = switch (season.rulesVersion()) {
            case V2026 -> driverStandings;
            case V2027 -> {
                var roster = Set.copyOf(season.roster(stage));
                yield driverStandings.stream().filter(roster::contains).toList();
            }
        };
        var actualPositions = new HashMap<String, Integer>();
        for (int i = 0; i < scoringStandings.size(); i++) {
            actualPositions.put(scoringStandings.get(i), i);
        }

        var points = stage == ChampionshipStage.PRE_SEASON ? PRE_SEASON_POINTS : MID_SEASON_POINTS;
        return IntStream.range(0, orderedDrivers.size()).mapToObj(predictedPosition -> {
            var actualPosition = actualPositions.get(orderedDrivers.get(predictedPosition));
            if (actualPosition == null) {
                return 0;
            }
            var difference = Math.abs(predictedPosition - actualPosition);
            return difference < points.length ? points[difference] : 0;
        }).toList();
    }
}
