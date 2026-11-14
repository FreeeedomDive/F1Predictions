package com.xdd.f1.bootstrap.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenTelemetryLoggingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryLoggingConfiguration.class);

    public OpenTelemetryLoggingConfiguration(OpenTelemetry openTelemetry) {
        try {
            OpenTelemetryAppender.install(openTelemetry);
        } catch (RuntimeException failure) {
            log.warn("Failed to install the OpenTelemetry Logback appender", failure);
        }
    }
}
