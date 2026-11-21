package com.xdd.f1.infrastructure.persistence.bingo;

import com.xdd.f1.application.bingo.BingoBoardNotFoundException;
import com.xdd.f1.application.bingo.BingoBoardRepository;
import com.xdd.f1.domain.bingo.BingoBoard;
import com.xdd.f1.domain.bingo.BingoBoardId;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipantId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class BingoBoardRepositoryAdapter implements BingoBoardRepository {
    private final BingoBoardJpaRepository repository;
    private final EntityManager entityManager;

    public BingoBoardRepositoryAdapter(BingoBoardJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<BingoBoard> findById(BingoBoardId id) {
        return repository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<BingoBoard> findByLeagueAndSeasonAndParticipant(
            LeagueId leagueId, int season, LeagueParticipantId participantId
    ) {
        return repository.findByLeagueIdAndSeasonAndParticipantId(leagueId.value(), season, participantId.value())
                .map(this::toDomain);
    }

    @Override
    public List<BingoBoard> findAllBySeason(int season) {
        return repository.findAllBySeason(season).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsBySeason(int season) {
        return repository.existsBySeason(season);
    }

    @Override
    public void create(BingoBoard board) {
        var entity = new BingoBoardEntity();
        entity.id = board.id().value();
        entity.leagueId = board.leagueId().value();
        entity.season = board.season();
        entity.participantId = board.participantId().value();
        entity.bingoAchieved = board.bingoAchieved();
        entity.cells = new ArrayList<>(board.cards().stream()
                .map(id -> new BingoBoardCellData(board.season(), id.value())).toList());
        entityManager.persist(entity);
    }

    @Override
    public void markAchieved(BingoBoardId id) {
        var entity = repository.findById(id.value()).orElseThrow(() -> new BingoBoardNotFoundException(id));
        entity.bingoAchieved = true;
    }

    private BingoBoard toDomain(BingoBoardEntity entity) {
        return new BingoBoard(new BingoBoardId(entity.id), new LeagueId(entity.leagueId), entity.season,
                new LeagueParticipantId(entity.participantId),
                entity.cells.stream().map(cell -> new BingoCardId(cell.cardId())).toList(), entity.bingoAchieved);
    }
}
