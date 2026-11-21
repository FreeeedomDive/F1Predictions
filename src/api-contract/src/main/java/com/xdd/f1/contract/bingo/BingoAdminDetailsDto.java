package com.xdd.f1.contract.bingo;

import java.util.List;

public record BingoAdminDetailsDto(int season, boolean structureEditable, boolean canCopyPrevious,
                                   List<BingoAdminCardDto> data) {
}
