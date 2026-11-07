package com.xdd.f1.contract.common;

import java.util.List;

public record ListResponseDto<T>(List<T> data) {
}
