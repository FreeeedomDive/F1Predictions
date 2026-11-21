package com.xdd.f1.infrastructure.persistence.bingo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BingoCardJpaRepository extends JpaRepository<BingoCardEntity, UUID> {
    List<BingoCardEntity> findAllBySeason(int season);
}
