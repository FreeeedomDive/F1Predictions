package com.xdd.f1.infrastructure.persistence.rights;

import com.xdd.f1.application.rights.RightsRepository;
import com.xdd.f1.application.rights.UserRights;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.UserId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Repository
public class RightsRepositoryAdapter implements RightsRepository {
    private final UserRightJpaRepository repository;

    public RightsRepositoryAdapter(UserRightJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<UserRights> findAllUsers() {
        var users = repository.findAllUsers().stream()
                .collect(groupingBy(UserRightsProjection::getUserId, LinkedHashMap::new, toList()));
        return users.entrySet().stream().map(entry -> new UserRights(
                new UserId(entry.getKey()),
                entry.getValue().stream().map(UserRightsProjection::getDisplayName)
                        .filter(Objects::nonNull).distinct().sorted().toList(),
                entry.getValue().stream().map(UserRightsProjection::getRightName)
                        .filter(Objects::nonNull).distinct().map(Right::valueOf).toList()
        )).toList();
    }

    @Override
    public List<Right> findByUserId(UserId userId) {
        return repository.findNamesByUserId(userId.value()).stream().map(Right::valueOf).toList();
    }

    @Override
    public void createIfAbsent(UserId userId, Right right) {
        repository.createIfAbsent(userId.value(), right.name());
    }

    @Override
    public void delete(UserId userId, Right right) {
        repository.delete(userId.value(), right.name());
    }
}
