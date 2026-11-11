package com.xdd.f1.contract.prediction;

public record PredictionPointsDto(
        String rulesVersion,
        int totalPoints,
        Integer tenthPlacePoints,
        Integer dnfsPoints,
        Integer safetyCarsPoints,
        Integer firstPlaceLeadPoints,
        Integer teamMatesPoints,
        Integer driverPositionPoints
) {
}
