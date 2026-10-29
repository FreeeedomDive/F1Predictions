package com.xdd.f1.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserIdentityJpaRepository extends JpaRepository<UserIdentityEntity, UserIdentityEntityId> {
}
