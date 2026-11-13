package com.xdd.f1.infrastructure.persistence.rights;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRightJpaRepository extends JpaRepository<UserRightEntity, UserRightEntityId> {
    @Query("""
        select distinct u.id as userId, p.displayName as displayName, r.id.rightName as rightName
        from UserEntity u
        left join u.leaguesParticipant p
        left join UserRightEntity r on r.id.userId = u.id
        order by u.id, p.displayName, r.id.rightName
        """)
    List<UserRightsProjection> findAllUsers();

    @Query("""
        select r.id.rightName
        from UserRightEntity r
        where r.id.userId = :userId
        order by r.id.rightName
        """)
    List<String> findNamesByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("""
        insert into UserRightEntity (id.userId, id.rightName)
        values (:userId, :rightName)
        on conflict (id.userId, id.rightName) do nothing
        """)
    int createIfAbsent(@Param("userId") UUID userId, @Param("rightName") String rightName);

    @Modifying
    @Query("""
        delete from UserRightEntity r
        where r.id.userId = :userId and r.id.rightName = :rightName
        """)
    int delete(@Param("userId") UUID userId, @Param("rightName") String rightName);
}
