package org.knowledgeroot.app.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Serialize and briefly cache probes; never attach exception messages or connection details. */
public final class DependencyHealthIndicator implements HealthIndicator {
    private final String dependency;
    private final Runnable probe;
    private final AtomicInteger available = new AtomicInteger(-1);
    private long nextCheck;
    private Health last;

    public DependencyHealthIndicator(String dependency, Runnable probe, MeterRegistry metrics) {
        this.dependency = dependency;
        this.probe = probe;
        metrics.gauge("knowledgeroot.dependency.available", java.util.List.of(
                io.micrometer.core.instrument.Tag.of("dependency", dependency)), available);
    }

    @Override public synchronized Health health() {
        if (last != null && System.nanoTime() < nextCheck) return last;
        int current;
        try { probe.run(); current = 1; }
        catch (RuntimeException failure) { current = 0; }
        if (available.getAndSet(current) != current) {
            var log = LoggerFactory.getLogger(DependencyHealthIndicator.class);
            if (current == 1) log.info("Dependency {} is UP", dependency);
            else log.warn("Dependency {} is DOWN; check its service, permissions and capacity", dependency);
        }
        last = current == 1 ? Health.up().build() : Health.down().build();
        nextCheck = System.nanoTime() + java.time.Duration.ofSeconds(2).toNanos();
        return last;
    }
}
