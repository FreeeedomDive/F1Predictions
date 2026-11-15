package com.xdd.f1.bootstrap.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            var pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            var route = pattern instanceof String matchedRoute ? matchedRoute : request.getRequestURI();
            int status = failure != null && !response.isCommitted()
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();

            var event = status >= 500 ? log.atError() : log.atInfo();
            if (failure != null) {
                event.setCause(failure).addKeyValue("error.type", failure.getClass().getName());
            }
            event.addKeyValue("http.request.method", request.getMethod())
                    .addKeyValue("http.route", route)
                    .addKeyValue("url.path", request.getRequestURI())
                    .addKeyValue("http.response.status_code", status)
                    .addKeyValue("http.server.request.duration_ms", millis)
                    .log("{} {} -> {} in {} ms", request.getMethod(), route, status, millis);
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        var path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/actuator");
    }
}
