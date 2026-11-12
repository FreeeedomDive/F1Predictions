package com.xdd.f1.infrastructure.persistence.user;

import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserIdentityJpaRepository userIdentityJpaRepository;
    private final EntityManager entityManager;

    public UserRepositoryAdapter(
            UserIdentityJpaRepository userIdentityJpaRepository,
            EntityManager entityManager
    ) {
        this.userIdentityJpaRepository = userIdentityJpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(entityManager.find(UserEntity.class, id.value())).map(this::toModel);
    }

    @Override
    public Optional<User> findByIdentity(UserIdentityId identityId) {
        var identity = userIdentityJpaRepository.findById(new UserIdentityEntityId(identityId.source().name(), identityId.sourceId()));
        return identity.map(userIdentityEntity -> toModel(userIdentityEntity.user));
    }

    @Override
    @Transactional
    public void create(User user, UserIdentityId identityId) {
        var entity = toEntity(user);
        var identityEntity = new UserIdentityEntity();
        identityEntity.id = new UserIdentityEntityId(identityId.source().name(), identityId.sourceId());
        identityEntity.user = entity;

        entity.userIdentities = new ArrayList<>();
        entity.userIdentities.add(identityEntity);

        entityManager.persist(entity);
    }

    private User toModel(UserEntity user) {
        return new User(new UserId(user.getId()), user.getCreatedAt());
    }

    private UserEntity toEntity(User user) {
        var entity = new UserEntity();
        entity.id = user.id().value();
        entity.createdAt = user.createdAt();
        return entity;
    }
}
