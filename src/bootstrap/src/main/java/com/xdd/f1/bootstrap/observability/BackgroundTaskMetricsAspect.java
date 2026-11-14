package com.xdd.f1.bootstrap.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class BackgroundTaskMetricsAspect {

    private final MeterRegistry registry;
    private final ConcurrentMap<TaskKey, TaskMeters> tasks = new ConcurrentHashMap<>();

    public BackgroundTaskMetricsAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("execution(* com.xdd.f1..*(..)) && @annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object scheduledTask(ProceedingJoinPoint invocation) throws Throwable {
        return measure(invocation, "scheduler");
    }

    @Around("execution(* com.xdd.f1..*(..)) && @annotation(org.springframework.modulith.events.ApplicationModuleListener)")
    public Object eventConsumer(ProceedingJoinPoint invocation) throws Throwable {
        return measure(invocation, "consumer");
    }

    private Object measure(ProceedingJoinPoint invocation, String kind) throws Throwable {
        var signature = invocation.getSignature();
        var key = new TaskKey(kind, signature.getDeclaringType().getSimpleName() + "." + signature.getName());
        var meters = tasks.computeIfAbsent(key, task -> new TaskMeters(registry, task));
        meters.active.incrementAndGet();
        meters.lastStart.set(registry.config().clock().wallTime());
        var sample = Timer.start(registry);
        boolean success = false;
        try {
            var result = invocation.proceed();
            success = true;
            meters.lastSuccess.set(registry.config().clock().wallTime());
            return result;
        } finally {
            sample.stop(success ? meters.success : meters.error);
            meters.active.decrementAndGet();
        }
    }

    private record TaskKey(String kind, String task) {
    }

    private static class TaskMeters {
        private final Timer success;
        private final Timer error;
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicLong lastStart = new AtomicLong();
        private final AtomicLong lastSuccess = new AtomicLong();

        TaskMeters(MeterRegistry registry, TaskKey task) {
            var tags = Tags.of("kind", task.kind(), "task", task.task());
            success = Timer.builder("f1.background.task.execution")
                    .description("Background handler executions and duration")
                    .tags(tags).tag("outcome", "success").register(registry);
            error = Timer.builder("f1.background.task.execution")
                    .description("Background handler executions and duration")
                    .tags(tags).tag("outcome", "error").register(registry);
            Gauge.builder("f1.background.task.active", active, AtomicInteger::get)
                    .description("Currently executing background handlers")
                    .tags(tags).register(registry);
            Gauge.builder("f1.background.task.last.start", lastStart, value -> value.get() / 1000.0)
                    .description("Unix timestamp of the last handler start; zero if never started")
                    .baseUnit("seconds").tags(tags).register(registry);
            Gauge.builder("f1.background.task.last.success", lastSuccess, value -> value.get() / 1000.0)
                    .description("Unix timestamp of the last successful handler execution; zero if none")
                    .baseUnit("seconds").tags(tags).register(registry);
        }
    }
}
