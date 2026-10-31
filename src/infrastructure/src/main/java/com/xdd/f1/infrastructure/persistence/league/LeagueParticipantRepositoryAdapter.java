package com.xdd.f1.infrastructure.persistence.league;

import com.xdd.f1.application.league.LeagueParticipantNotFoundException;
import com.xdd.f1.application.league.LeagueParticipantRepository;
import com.xdd.f1.application.league.UserLeague;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.infrastructure.persistence.user.UserJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class LeagueParticipantRepositoryAdapter implements LeagueParticipantRepository {

    private final LeagueParticipantJpaRepository leagueParticipantJpaRepository;
    private final LeagueJpaRepository leagueJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public LeagueParticipantRepositoryAdapter(
            LeagueParticipantJpaRepository leagueParticipantJpaRepository,
            LeagueJpaRepository leagueJpaRepository,
            UserJpaRepository userJpaRepository
    ) {
        this.leagueParticipantJpaRepository = leagueParticipantJpaRepository;
        this.leagueJpaRepository = leagueJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public Optional<LeagueParticipant> findByLeagueAndUser(LeagueId leagueId, UserId userId) {
        var entity = leagueParticipantJpaRepository.findByLeague_IdAndUser_Id(leagueId.value(), userId.value());
        return entity.map(this::toDomain);
    }

    @Override
    public List<UserLeague> findLeaguesByUserId(UserId userId) {
        return leagueParticipantJpaRepository.findLeaguesByUserId(userId.value())
                .stream()
                .map(row -> new UserLeague(
                        new LeagueId(row.getLeagueId()),
                        new LeagueParticipantId(row.getParticipantId()),
                        row.getLeagueName(),
                        row.getParticipantName()
                ))
                .toList();
    }

    @Override
    public void create(LeagueParticipant participant) {
        leagueParticipantJpaRepository.save(toEntity(participant));
    }

    @Override
    public LeagueParticipant updateDisplayName(LeagueParticipantId id, String newDisplayName) {
        var participant = leagueParticipantJpaRepository.findById(id.value()).orElseThrow(() -> new LeagueParticipantNotFoundException(id));
        participant.displayName = newDisplayName;
        return toDomain(participant);
    }

    private LeagueParticipant toDomain(LeagueParticipantEntity leagueParticipantEntity) {
        return new LeagueParticipant(
                new LeagueParticipantId(leagueParticipantEntity.id),
                new LeagueId(leagueParticipantEntity.league.getId()),
                new UserId(leagueParticipantEntity.user.getId()),
                leagueParticipantEntity.displayName,
                leagueParticipantEntity.joinedAt
        );
    }

    private LeagueParticipantEntity toEntity(LeagueParticipant participant) {
        var entity = new LeagueParticipantEntity();
        entity.id = participant.id().value();
        entity.league = leagueJpaRepository.getReferenceById(
                participant.leagueId().value()
        );
        entity.user = userJpaRepository.getReferenceById(
                participant.userId().value()
        );
        entity.displayName = participant.displayName();
        entity.joinedAt = participant.joinedAt();
        return entity;
    }
}
