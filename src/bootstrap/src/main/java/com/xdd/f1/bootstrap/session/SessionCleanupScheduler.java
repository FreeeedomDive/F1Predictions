package com.xdd.f1.bootstrap.session;

import com.xdd.f1.application.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final SessionService sessions;

    public SessionCleanupScheduler(SessionService sessions) {
        this.sessions = sessions;
    }

    @Scheduled(
            fixedDelayString = "${sessions.cleanup-interval}",
            initialDelayString = "${sessions.cleanup-interval}"
    )
    public void deleteExpiredSessions() {
        var deleted = sessions.deleteExpired();
        if (deleted > 0) {
            log.info("Deleted {} expired sessions", deleted);
        }
    }
}
