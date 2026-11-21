package com.xdd.f1.contract.bingo;

public record BingoDetailsDto(int season, int activeSeason, boolean canCreate, BingoBoardDto board) {
}
