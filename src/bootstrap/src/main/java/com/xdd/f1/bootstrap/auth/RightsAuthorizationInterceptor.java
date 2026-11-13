package com.xdd.f1.bootstrap.auth;

import com.xdd.f1.application.rights.RightsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class RightsAuthorizationInterceptor implements HandlerInterceptor {
    private final RightsService rights;

    public RightsAuthorizationInterceptor(RightsService rights) {
        this.rights = rights;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || !(handler instanceof HandlerMethod method)) {
            return true;
        }

        if (!(request.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) instanceof AuthenticatedUser user)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        var requirement = method.getMethodAnnotation(RequireRights.class);
        if (requirement == null) {
            requirement = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequireRights.class);
        }
        if (requirement != null) {
            var granted = rights.findByUserId(user.id());
            if (Arrays.stream(requirement.value()).anyMatch(granted::contains)) {
                return true;
            }
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }
}
