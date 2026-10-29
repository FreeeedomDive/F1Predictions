package com.xdd.f1.application.user;

import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserIdentityId;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findByIdentity(UserIdentityId identityId);

    void create(User user, UserIdentityId identityId);
}