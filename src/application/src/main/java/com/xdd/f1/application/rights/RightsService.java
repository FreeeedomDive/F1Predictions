package com.xdd.f1.application.rights;

import com.xdd.f1.application.user.UserNotFoundException;
import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RightsService {
    private final RightsRepository repository;
    private final UserRepository users;

    public RightsService(RightsRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<UserRights> findAllUsers() {
        return repository.findAllUsers();
    }

    @Transactional(readOnly = true)
    public List<Right> findByUserId(UserId userId) {
        return repository.findByUserId(userId);
    }

    @Transactional
    public void grant(UserId userId, Right right) {
        requireUser(userId);
        repository.createIfAbsent(userId, right);
    }

    @Transactional
    public void revoke(UserId userId, Right right) {
        requireUser(userId);
        repository.delete(userId, right);
    }

    private void requireUser(UserId userId) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
    }
}
