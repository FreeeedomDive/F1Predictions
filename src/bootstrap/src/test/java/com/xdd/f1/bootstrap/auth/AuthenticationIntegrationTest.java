package com.xdd.f1.bootstrap.auth;

import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.application.user.UserRepository;
import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import com.xdd.f1.domain.user.User;
import com.xdd.f1.domain.user.UserId;
import com.xdd.f1.domain.user.UserIdentityId;
import com.xdd.f1.domain.user.UserIdentitySource;
import com.xdd.f1.infrastructure.persistence.user.UserJpaRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-02-03T04:05:06Z");

    private final MockMvc mvc;
    private final SessionService sessions;
    private final UserRepository users;
    private final UserJpaRepository userJpaRepository;

    @MockitoBean
    private Clock clock;

    private UserId userId;

    @Autowired
    AuthenticationIntegrationTest(
            MockMvc mvc,
            SessionService sessions,
            UserRepository users,
            UserJpaRepository userJpaRepository
    ) {
        this.mvc = mvc;
        this.sessions = sessions;
        this.users = users;
        this.userJpaRepository = userJpaRepository;
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        userId = new UserId(UUID.randomUUID());
        users.create(
                new User(userId, NOW),
                new UserIdentityId(UserIdentitySource.MANUAL, UUID.randomUUID().toString())
        );
    }

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            userJpaRepository.deleteById(userId.value());
        }
    }

    @Test
    void logsInDespiteStaleCookieAndUsesNewCookieForMe() throws Exception {
        var token = sessions.create(userId);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(1)));

        var response = mvc.perform(post("/api/v1/auth")
                        .cookie(new Cookie(SessionCookie.NAME, "stale-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.value().toString()))
                .andExpect(cookie().value(SessionCookie.NAME, token))
                .andExpect(cookie().httpOnly(SessionCookie.NAME, true))
                .andExpect(cookie().secure(SessionCookie.NAME, true))
                .andExpect(cookie().path(SessionCookie.NAME, "/"))
                .andExpect(cookie().maxAge(SessionCookie.NAME, (int) Duration.ofDays(6).toSeconds()))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andReturn()
                .getResponse();

        var sessionCookie = response.getCookie(SessionCookie.NAME);
        assertThat(sessionCookie).isNotNull();
        mvc.perform(get("/api/v1/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.value().toString()));

        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMeWithoutCookieEvenWhenUserIdIsProvided() throws Exception {
        mvc.perform(get("/api/v1/me").param("userId", userId.value().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownSessionCookie() throws Exception {
        mvc.perform(get("/api/v1/me").cookie(new Cookie(SessionCookie.NAME, "unknown-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsSessionCookieAtExactExpiry() throws Exception {
        var token = sessions.create(userId);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(7)));

        mvc.perform(get("/api/v1/me").cookie(new Cookie(SessionCookie.NAME, token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownLoginTokenWithoutSettingCookie() throws Exception {
        mvc.perform(post("/api/v1/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"unknown-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void leavesHealthEndpointOutsideApiAuthentication() throws Exception {
        mvc.perform(get("/actuator/health").cookie(new Cookie(SessionCookie.NAME, "unknown-token")))
                .andExpect(status().isOk());
    }
}
