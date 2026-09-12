package org.knowledgeroot.app.file.db;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class StorageConfig {
    @Bean
    FileStorage storageImplementation(Environment environment) {
        return switch (environment.getProperty("knowledgeroot.storage.driver", "minio")) {
            case "file" -> new FileSystemStorage(environment.getProperty("file.storage-dir", "./storage"));
            case "minio" -> new MinioStorage(environment.getRequiredProperty("minio.url"),
                    environment.getRequiredProperty("minio.access-key"), environment.getRequiredProperty("minio.secret-key"),
                    environment.getRequiredProperty("minio.bucket"));
            default -> throw new IllegalStateException("Unknown storage driver; use file or minio");
        };
    }
}
