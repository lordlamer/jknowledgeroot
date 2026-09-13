package org.knowledgeroot.app.file.db;

import io.micrometer.core.instrument.MeterRegistry;
import org.knowledgeroot.app.config.DependencyHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class StorageHealthConfig {
    @Bean
    DependencyHealthIndicator storageHealthIndicator(FileStorage storage, MeterRegistry metrics) {
        return new DependencyHealthIndicator("storage", storage::checkAvailability, metrics);
    }
}
