package com.xdd.f1.bootstrap.race;

import com.xdd.f1.infrastructure.http.jolpica.JolpicaClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(JolpicaProperties.class)
public class JolpicaConfiguration {

    @Bean
    public HttpClient jolpicaHttpClient(JolpicaProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    @Bean
    public JolpicaClient jolpicaClient(
            RestClient.Builder builder,
            JolpicaProperties properties,
            @Qualifier("jolpicaHttpClient") HttpClient httpClient
    ) {
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        var restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
        var factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(JolpicaClient.class);
    }
}
