package com.xdd.f1.infrastructure.persistence.league;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeagueParticipantJpaRepository extends JpaRepository<LeagueParticipantEntity, UUID> {
    Optional<LeagueParticipantEntity> findByLeague_IdAndUser_Id(
            UUID leagueId,
            UUID userId
    );

    @Query("""
        select l.id as leagueId,
               p.id as participantId,
               l.name as leagueName,
               p.displayName as participantName
        from LeagueParticipantEntity p
        join p.league l
        where p.user.id = :userId
        order by p.joinedAt
        """)
    List<UserLeagueProjection> findLeaguesByUserId(
            @Param("userId") UUID userId
    );
}
