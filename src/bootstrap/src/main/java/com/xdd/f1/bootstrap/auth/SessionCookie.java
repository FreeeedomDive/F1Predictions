package com.xdd.f1.bootstrap.auth;

import org.springframework.http.ResponseCookie;

public final class SessionCookie {

    public static final String NAME = "f1_session";

    public static ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/");
    }

    private SessionCookie() {
    }
}
