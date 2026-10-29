package com.xdd.f1.infrastructure.persistence.user;

import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Optional;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserIdentityJpaRepository userIdentityJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public UserRepositoryAdapter(
            UserIdentityJpaRepository userIdentityJpaRepository,
            UserJpaRepository userJpaRepository
    ) {
        this.userIdentityJpaRepository = userIdentityJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public Optional<User> findByIdentity(UserIdentityId identityId) {
        var identity = userIdentityJpaRepository.findById(new UserIdentityEntityId(identityId.source().name(), identityId.sourceId()));
        return identity.map(userIdentityEntity -> toModel(userIdentityEntity.user));
    }

    @Override
    public void create(User user, UserIdentityId identityId) {
        var entity = toEntity(user);
        var identityEntity = new UserIdentityEntity();
        identityEntity.id = new UserIdentityEntityId(identityId.source().name(), identityId.sourceId());
        identityEntity.user = entity;

        entity.userIdentities = new ArrayList<>();
        entity.userIdentities.add(identityEntity);

        userJpaRepository.save(entity);
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
