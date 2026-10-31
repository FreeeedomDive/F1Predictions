package com.xdd.f1.application.session;

import com.xdd.f1.domain.session.Session;
import com.xdd.f1.domain.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final Clock clock;
    private final SecretKey sessionHashKey;
    private static final Duration SESSION_TTL = Duration.ofDays(7);

    public SessionService(SessionRepository sessionRepository, Clock clock, SecretKey sessionHashKey) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.sessionHashKey = sessionHashKey;
    }

    @Transactional
    public String create(UserId userId) {
        var token = UUID.randomUUID().toString();
        var hash = getHash(token);
        var now = clock.instant();
        var session = new Session(hash, userId, now, now.plus(SESSION_TTL), null);
        sessionRepository.create(session);

        return token;
    }

    @Transactional
    public Optional<Session> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        var hash = getHash(token);
        var session = sessionRepository.findByHash(hash);
        var now = clock.instant();
        if (session.isEmpty() || session.get().isExpired(now)){
            return Optional.empty();
        }

        sessionRepository.updateLastUsedAt(hash, now);

        return session;
    }

    @Transactional
    public int deleteExpired() {
        return sessionRepository.deleteExpired(clock.instant());
    }

    private String getHash(String token) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(sessionHashKey);
            var bytes = mac.doFinal(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(bytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Failed to compute the session token HMAC",
                    exception
            );
        }
    }

}
