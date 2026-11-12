package com.xdd.f1.contract.historyimport;

import com.xdd.f1.contract.prediction.PredictionPointsDto;

import java.util.List;
import java.util.UUID;

public record ImportReportDto(int created, int existing, int updated,
                              List<PointsDifference> pointsDifferences) {
    public record PointsDifference(UUID raceId, UUID userId, PredictionPointsDto imported, PredictionPointsDto calculated) {
    }
}
