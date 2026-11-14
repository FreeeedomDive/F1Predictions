package com.xdd.f1.bootstrap.auth;

import com.xdd.f1.application.league.LeagueParticipantRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalAuthenticationConfiguration implements WebMvcConfigurer {
    private final InternalAuthenticationInterceptor authentication;
    private final LeagueParticipantRepository participants;

    public InternalAuthenticationConfiguration(InternalAuthenticationInterceptor authentication, LeagueParticipantRepository participants) {
        this.authentication = authentication;
        this.participants = participants;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(authentication).addPathPatterns("/internal/v1/**");
    }

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new InternalCurrentParticipantArgumentResolver(participants));
    }
}
