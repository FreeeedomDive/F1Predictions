package com.xdd.f1.infrastructure.persistence.rights;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_rights")
public class UserRightEntity {
    @EmbeddedId
    UserRightEntityId id;

    protected UserRightEntity() {
    }
}
