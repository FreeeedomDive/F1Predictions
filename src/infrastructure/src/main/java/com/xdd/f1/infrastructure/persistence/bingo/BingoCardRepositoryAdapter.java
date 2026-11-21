package com.xdd.f1.infrastructure.persistence.bingo;

import com.xdd.f1.application.bingo.BingoCardNotFoundException;
import com.xdd.f1.application.bingo.BingoCardRepository;
import com.xdd.f1.domain.bingo.BingoCard;
import com.xdd.f1.domain.bingo.BingoCardId;
import com.xdd.f1.domain.bingo.BingoCardProbability;
import com.xdd.f1.domain.bingo.BingoCardTranslation;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class BingoCardRepositoryAdapter implements BingoCardRepository {
    private final BingoCardJpaRepository repository;
    private final EntityManager entityManager;

    public BingoCardRepositoryAdapter(BingoCardJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<BingoCard> findById(BingoCardId id) {
        return repository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<BingoCard> findAllBySeason(int season) {
        return repository.findAllBySeason(season).stream().map(this::toDomain).toList();
    }

    @Override
    public void create(BingoCard card) {
        var entity = new BingoCardEntity();
        entity.id = card.id().value();
        entity.season = card.season();
        setDefinition(entity, card);
        entity.completedRepeats = card.completedRepeats();
        entityManager.persist(entity);
    }

    @Override
    public void updateDefinition(BingoCard card) {
        var entity = repository.findById(card.id().value()).orElseThrow(() -> new BingoCardNotFoundException(card.id()));
        setDefinition(entity, card);
    }

    @Override
    public void delete(BingoCardId id) {
        repository.deleteById(id.value());
    }

    @Override
    public void updateProgress(BingoCardId id, int completedRepeats) {
        var entity = repository.findById(id.value()).orElseThrow(() -> new BingoCardNotFoundException(id));
        entity.completedRepeats = completedRepeats;
    }

    private void setDefinition(BingoCardEntity entity, BingoCard card) {
        entity.translations = card.translations().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new BingoCardTranslationData(entry.getValue().description(), entry.getValue().explanation())
        ));
        entity.probability = card.probability().name();
        entity.requiredRepeats = card.requiredRepeats();
    }

    private BingoCard toDomain(BingoCardEntity entity) {
        var translations = entity.translations.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new BingoCardTranslation(entry.getValue().description(), entry.getValue().explanation())
        ));
        return new BingoCard(new BingoCardId(entity.id), entity.season, translations,
                BingoCardProbability.valueOf(entity.probability), entity.requiredRepeats, entity.completedRepeats);
    }
}
