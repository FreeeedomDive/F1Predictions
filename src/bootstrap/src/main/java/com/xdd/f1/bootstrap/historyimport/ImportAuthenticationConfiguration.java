package com.xdd.f1.bootstrap.historyimport;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImportProperties.class)
public class ImportAuthenticationConfiguration implements WebMvcConfigurer {
    private final ImportApiKeyInterceptor authentication;

    public ImportAuthenticationConfiguration(ImportApiKeyInterceptor authentication) {
        this.authentication = authentication;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(authentication).addPathPatterns("/importApi/**");
    }
}
