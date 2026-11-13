package com.xdd.f1.contract.rights;

import java.util.List;
import java.util.UUID;

public record UserRightsDto(UUID userId, List<String> names, List<RightDto> rights) {
}
