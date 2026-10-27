package com.xdd.f1.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots the whole application against a throwaway Postgres: migrations run, the JPA mappings are
 * validated against them, and every bean is wired. The cheapest way to catch a migration that
 * does not match the entities.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ApplicationContextTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void contextLoads() {
    }
}
