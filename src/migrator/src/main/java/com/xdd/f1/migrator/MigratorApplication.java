package com.xdd.f1.migrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Applies the Flyway migrations from the schema module and exits. */
@SpringBootApplication
public class MigratorApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(MigratorApplication.class, args)));
    }
}
