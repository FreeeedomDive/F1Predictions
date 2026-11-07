package com.xdd.f1.bootstrap;

import org.springframework.boot.task.SimpleAsyncTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class SchedulingConfiguration {

    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    @Primary
    public SimpleAsyncTaskExecutor applicationTaskExecutor(SimpleAsyncTaskExecutorBuilder builder) {
        return builder.threadNamePrefix("application-").build();
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }

    @Bean
    public ThreadPoolTaskScheduler raceCalendarTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("race-calendar-sync-");
        return scheduler;
    }
}
