package com.xdd.f1.bootstrap.race;

import com.xdd.f1.infrastructure.persistence.race.jolpica.JolpicaClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
@EnableConfigurationProperties(JolpicaProperties.class)
public class JolpicaConfiguration {

    @Bean
    public JolpicaClient jolpicaClient(RestClient.Builder builder, JolpicaProperties properties) {
        var restClient = builder.baseUrl(properties.baseUrl()).build();
        var factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(JolpicaClient.class);
    }
}
