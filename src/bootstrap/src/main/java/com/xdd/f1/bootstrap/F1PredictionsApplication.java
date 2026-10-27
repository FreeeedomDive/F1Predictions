package com.xdd.f1.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.xdd.f1")
public class F1PredictionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(F1PredictionsApplication.class, args);
    }
}
