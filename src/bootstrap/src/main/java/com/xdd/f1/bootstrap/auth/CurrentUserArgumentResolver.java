package com.xdd.f1.bootstrap.auth;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType() == AuthenticatedUser.class;
    }

    @Override
    public @NonNull Object resolveArgument(
            @NonNull MethodParameter parameter,
            @Nullable ModelAndViewContainer container,
            @NonNull NativeWebRequest request,
            @Nullable WebDataBinderFactory binderFactory
    ) {
        var attribute = request.getAttribute(
                AuthenticatedUser.REQUEST_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST
        );

        if (attribute instanceof AuthenticatedUser user) {
            return user;
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
