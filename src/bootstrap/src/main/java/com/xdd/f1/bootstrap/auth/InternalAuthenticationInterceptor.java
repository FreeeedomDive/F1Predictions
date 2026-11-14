package com.xdd.f1.bootstrap.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalAuthenticationInterceptor implements HandlerInterceptor {
    private final InternalApiProperties properties;

    public InternalAuthenticationInterceptor(InternalApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        var key = properties.serviceKey();
        var authorization = request.getHeader("Authorization");
        if (key == null || key.isBlank() || authorization == null
                || !MessageDigest.isEqual(("Bearer " + key).getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }
}
