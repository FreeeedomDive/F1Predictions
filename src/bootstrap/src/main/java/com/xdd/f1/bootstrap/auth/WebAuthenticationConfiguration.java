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

    public WebAuthenticationConfiguration(SessionAuthenticationInterceptor authentication) {
        this.authentication = authentication;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(authentication).addPathPatterns("/api/v1/**");
    }

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
