package org.knowledgeroot.app.file.db;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
class StorageConfig {
    @Value("${knowledgeroot.storage.driver}")
    private String storageDriver;

    private final FileSystemStorage fileSystemStorage;
    private final MinioStorage minioStorage;

    @Bean
    @Primary
    public FileStorage storageImplementation() {
        switch(storageDriver) {
            case "file":
                return fileSystemStorage;
            case "minio":
                return minioStorage;
            default:
                throw new IllegalStateException("Unknown storage driver: " + storageDriver);
        }
    }
}
