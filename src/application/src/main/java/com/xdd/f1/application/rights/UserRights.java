package com.xdd.f1.application.rights;

import com.xdd.f1.domain.rights.Right;
import com.xdd.f1.domain.user.UserId;

import java.util.List;

public record UserRights(UserId userId, List<String> names, List<Right> rights) {
}
