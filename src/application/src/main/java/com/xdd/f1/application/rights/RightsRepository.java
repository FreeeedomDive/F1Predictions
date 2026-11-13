package com.xdd.f1.application.rights;

import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.UserId;

import java.util.List;

public interface RightsRepository {
    List<UserRights> findAllUsers();

    List<Right> findByUserId(UserId userId);

    void createIfAbsent(UserId userId, Right right);

    void delete(UserId userId, Right right);
}
