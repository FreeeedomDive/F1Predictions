package com.xdd.f1.application.user;

import com.xdd.f1.domain.user.UserId;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UserId id) {
        super("User not found with id: " + id.value());
    }
}
