package com.xdd.f1.application.league;

import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.domain.league.LeagueId;
import com.xdd.f1.domain.league.LeagueParticipant;
import com.xdd.f1.domain.league.LeagueParticipantId;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class LeagueParticipantService {

    private final UserRepository userRepository;
    private final LeagueParticipantRepository leagueParticipantRepository;

    public LeagueParticipantService(
            UserRepository userRepository,
            LeagueParticipantRepository leagueParticipantRepository
    ) {
        this.userRepository = userRepository;
        this.leagueParticipantRepository = leagueParticipantRepository;
    }

    @Transactional
    public LeagueParticipant ensureParticipant(LeagueId leagueId, UserIdentityId identityId, String displayName) {
        var user = userRepository.findByIdentity(identityId)
                .orElseGet(() -> {
                    var created = new User(
                            new UserId(UUID.randomUUID()),
                            Instant.now()
                    );

                    userRepository.create(created, identityId);
                    return created;
                });
        var participant = leagueParticipantRepository.findByLeagueAndUser(leagueId, user.id());
        if (participant.isEmpty()) {
            var participantId = new LeagueParticipantId(UUID.randomUUID());
            participant = Optional.of(new LeagueParticipant(participantId, leagueId, user.id(), displayName, Instant.now()));
            leagueParticipantRepository.create(participant.get());

            return participant.get();
        }

        if (participant.get().displayName().equals(displayName)) {
            return participant.get();
        }

        return leagueParticipantRepository.updateDisplayName(participant.get().id(), displayName);
    }
}
