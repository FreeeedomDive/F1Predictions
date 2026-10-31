package com.xdd.f1.bootstrap.session;

import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.contract.session.SessionApi;
import com.xdd.f1.domain.user.UserId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SessionApiAdapter implements SessionApi {

    private final SessionService sessionService;

    public SessionApiAdapter(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public String create(UUID userId) {
        return sessionService.create(new UserId(userId));
    }
}
