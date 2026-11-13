package com.xdd.f1.bootstrap.historyimport;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ImportApiKeyInterceptor implements HandlerInterceptor {
    private final ImportProperties properties;

    public ImportApiKeyInterceptor(ImportProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        var expected = properties.apiKey();
        var provided = request.getHeader("X-Import-Api-Key");
        if (expected == null || expected.isBlank() || provided == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }
}
