package com.xdd.f1.contract.user;

import java.util.UUID;
import java.util.List;

public record MeResponseDto(UUID userId, List<UserLeagueDto> leagues) {
}
