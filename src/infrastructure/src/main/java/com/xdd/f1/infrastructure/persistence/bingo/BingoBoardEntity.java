package com.xdd.f1.infrastructure.persistence.bingo;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bingo_boards")
public class BingoBoardEntity {
    @Id
    @Column(name = "id", nullable = false)
    UUID id;

    @Column(name = "league_id", nullable = false)
    UUID leagueId;

    @Column(name = "season", nullable = false)
    int season;

    @Column(name = "participant_id", nullable = false)
    UUID participantId;

    @Column(name = "bingo_achieved", nullable = false)
    boolean bingoAchieved;

    @ElementCollection
    @CollectionTable(name = "bingo_board_cells", joinColumns = @JoinColumn(name = "board_id"))
    @OrderColumn(name = "position", nullable = false)
    List<BingoBoardCellData> cells;

    protected BingoBoardEntity() {
    }
}
