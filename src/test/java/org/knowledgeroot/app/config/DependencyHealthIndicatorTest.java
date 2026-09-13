package org.knowledgeroot.app.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DependencyHealthIndicatorTest {
    @Test void repeatedRequestsShareAProbeAndPublishALowCardinalityGauge() {
        var metrics = new SimpleMeterRegistry();
        try {
            var calls = new AtomicInteger();
            var health = new DependencyHealthIndicator("storage",calls::incrementAndGet,metrics);
            assertEquals("UP",health.health().getStatus().getCode());
            assertEquals("UP",health.health().getStatus().getCode());
            assertEquals(1,calls.get());
            assertEquals(1,metrics.get("knowledgeroot.dependency.available").tag("dependency","storage").gauge().value());
        } finally { metrics.close(); }
    }

    @Test void failuresExposeNeitherMessagesNorExceptionDetails() {
        var metrics = new SimpleMeterRegistry();
        try {
            var health = new DependencyHealthIndicator("database",() -> { throw new IllegalStateException("password=private"); },metrics);
            assertEquals("DOWN",health.health().getStatus().getCode());
            assertTrue(health.health().getDetails().isEmpty());
            assertEquals(0,metrics.get("knowledgeroot.dependency.available").tag("dependency","database").gauge().value());
        } finally { metrics.close(); }
    }
}
