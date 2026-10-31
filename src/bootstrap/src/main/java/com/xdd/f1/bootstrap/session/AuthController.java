package com.xdd.f1.bootstrap.session;

import com.xdd.f1.application.session.SessionService;
import com.xdd.f1.bootstrap.auth.AllowAnonymous;
import com.xdd.f1.bootstrap.auth.SessionCookie;
import com.xdd.f1.contract.session.AuthRequestDto;
import com.xdd.f1.contract.session.AuthResponseDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

@AllowAnonymous
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SessionService sessionService;
    private final Clock clock;

    public AuthController(SessionService sessionService, Clock clock) {
        this.sessionService = sessionService;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<AuthResponseDto> auth(@RequestBody AuthRequestDto request){
        var session = sessionService.find(request.token());
        if (session.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var maxAgeSeconds = Duration.between(
                clock.instant(),
                session.get().expiresAt()
        ).getSeconds();

        if (maxAgeSeconds <= 0) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var cookie = SessionCookie.builder(request.token())
                .maxAge(maxAgeSeconds)
                .build();

        var response = new AuthResponseDto(session.get().userId().value());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);

    }

    @DeleteMapping
    public ResponseEntity<Void> logout(
            @CookieValue(name = SessionCookie.NAME, required = false) String token
    ) {
        sessionService.revoke(token);
        var cookie = SessionCookie.builder("").maxAge(0).build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
