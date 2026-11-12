package com.xdd.f1.contract.statistics;

import com.xdd.f1.contract.prediction.SafetyCarsCountDto;

public record SeasonSafetyCarStatsDto(SafetyCarsCountDto safetyCars, int score) {
}
