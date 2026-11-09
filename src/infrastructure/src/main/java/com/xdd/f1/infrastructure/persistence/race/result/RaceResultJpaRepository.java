package com.xdd.f1.infrastructure.persistence.race.result;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RaceResultJpaRepository extends JpaRepository<RaceResultEntity, UUID> {
}
