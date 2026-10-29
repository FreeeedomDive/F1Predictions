package com.xdd.f1.bootstrap;

import com.xdd.f1.bootstrap.base.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Boots the whole application against a throwaway Postgres: migrations run, the JPA mappings are
 * validated against them, and every bean is wired. The cheapest way to catch a migration that
 * does not match the entities.
 */
class ApplicationContextTest extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
