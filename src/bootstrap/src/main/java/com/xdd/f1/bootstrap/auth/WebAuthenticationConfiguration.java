package com.xdd.f1.bootstrap.auth;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class WebAuthenticationConfiguration implements WebMvcConfigurer {

    private final SessionAuthenticationInterceptor authentication;
    private final RightsAuthorizationInterceptor authorization;

    public WebAuthenticationConfiguration(
            SessionAuthenticationInterceptor authentication,
            RightsAuthorizationInterceptor authorization
    ) {
        this.authentication = authentication;
        this.authorization = authorization;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(authentication).addPathPatterns("/api/v1/**", "/adminApi/**").order(0);
        registry.addInterceptor(authorization).addPathPatterns("/adminApi/**").order(1);
    }

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
