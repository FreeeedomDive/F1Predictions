package com.xdd.f1.bootstrap;

import com.xdd.f1.bootstrap.race.RaceCalendarSyncProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RaceCalendarSyncProperties.class)
public class ApplicationConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecretKey sessionHashKey(@Value("${sessions.hash-secret}") String secret) {
        if (secret.isBlank()) {
            throw new IllegalStateException("sessions.hash-secret must not be blank");
        }

        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

}
