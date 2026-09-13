package org.knowledgeroot.app.file.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.knowledgeroot.app.file.domain.StorageException;
import org.knowledgeroot.app.file.domain.StoredFileNotFoundException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class StorageDriversTest {
    @Container static final GenericContainer<?> minio = new GenericContainer<>("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withEnv("MINIO_ROOT_USER", "storage-test-user").withEnv("MINIO_ROOT_PASSWORD", "storage-test-password")
            .withCommand("server /data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    @TempDir Path directory;
    private static final String KEY = "sha256-" + "a".repeat(64);

    @Test
    void fileConfigurationNeedsNoMinioPropertiesOrConnection() {
        new ApplicationContextRunner().withUserConfiguration(StorageConfig.class)
                .withPropertyValues("knowledgeroot.storage.driver=file", "file.storage-dir=" + directory,
                        "minio.url=not-a-url", "minio.access-key=", "minio.secret-key=")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertInstanceOf(FileSystemStorage.class, context.getBean(FileStorage.class));
                    assertTrue(context.getBeansOfType(MinioStorage.class).isEmpty());
                });
    }

    @Test
    void minioConfigurationDoesNotCreateLocalStorage() {
        Path unused = directory.resolve("unused");
        new ApplicationContextRunner().withUserConfiguration(StorageConfig.class)
                .withPropertyValues("knowledgeroot.storage.driver=minio", "file.storage-dir=" + unused,
                        "minio.url=" + endpoint(), "minio.access-key=storage-test-user", "minio.secret-key=storage-test-password",
                        "minio.bucket=config-" + UUID.randomUUID())
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertInstanceOf(MinioStorage.class, context.getBean(FileStorage.class));
                    assertFalse(Files.exists(unused));
                });
    }

    @Test
    void unknownDriverFailsClearly() {
        new ApplicationContextRunner().withUserConfiguration(StorageConfig.class)
                .withPropertyValues("knowledgeroot.storage.driver=unknown")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"file", "minio"})
    void bothDriversRoundTripAndDistinguishMissingObjects(String driver) throws Exception {
        FileStorage storage = storage(driver);
        try {
            assertDoesNotThrow(storage::checkAvailability);
            assertFalse(storage.exists(KEY));
            assertThrows(StoredFileNotFoundException.class, () -> storage.retrieve(KEY));
            byte[] content = new byte[11 * 1024 * 1024];
            new java.util.Random(42).nextBytes(content);
            storage.store(KEY, new ByteArrayInputStream(content));
            assertTrue(storage.exists(KEY));
            try (var input = storage.retrieve(KEY)) { assertArrayEquals(content, input.readAllBytes()); }
            storage.delete(KEY);
            assertFalse(storage.exists(KEY));
        } finally { close(storage); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"file", "minio"})
    void simultaneousSameContentUploadsPublishCompleteObjects(String driver) throws Exception {
        FileStorage first = storage(driver);
        // Two local instances exercise publication across independent drivers sharing one directory.
        FileStorage second = driver.equals("file") ? new FileSystemStorage(directory.toString()) : first;
        byte[] bytes = new byte[128 * 1024];
        new java.util.Random(1).nextBytes(bytes);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = java.util.stream.IntStream.range(0, 8).<java.util.concurrent.Callable<Void>>mapToObj(index -> () -> {
                (index % 2 == 0 ? first : second).store(KEY, new ByteArrayInputStream(bytes));
                return null;
            }).toList();
            for (var future : executor.invokeAll(tasks)) future.get();
            try (var input = first.retrieve(KEY)) { assertArrayEquals(bytes, input.readAllBytes()); }
        } finally { close(first); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secret", "C:\\secret", "/secret", "", "sha256-bad"})
    void invalidKeysCannotAccessLocalFiles(String key) {
        var storage = new FileSystemStorage(directory.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.exists(key));
        assertThrows(IllegalArgumentException.class, () -> storage.retrieve(key));
        assertThrows(IllegalArgumentException.class, () -> storage.delete(key));
    }

    @Test
    void missingBucketIsAnErrorRatherThanAMissingObject() throws Exception {
        String bucket = "missing-" + UUID.randomUUID();
        try (var storage = new MinioStorage(endpoint(), "storage-test-user", "storage-test-password", bucket)) {
            var admin = io.minio.MinioClient.builder().endpoint(endpoint()).credentials("storage-test-user", "storage-test-password").build();
            admin.removeBucket(io.minio.RemoveBucketArgs.builder().bucket(bucket).build());
            assertThrows(StorageException.class, () -> storage.exists(KEY));
            assertThrows(StorageException.class, storage::checkAvailability);
        }
    }

    private FileStorage storage(String driver) {
        return driver.equals("file") ? new FileSystemStorage(directory.toString())
                : new MinioStorage(endpoint(), "storage-test-user", "storage-test-password", "objects-" + UUID.randomUUID());
    }
    private String endpoint() { return "http://" + minio.getHost() + ":" + minio.getMappedPort(9000); }
    private void close(FileStorage storage) throws Exception { if (storage instanceof AutoCloseable closeable) closeable.close(); }
}
