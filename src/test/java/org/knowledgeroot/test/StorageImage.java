package org.knowledgeroot.test;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * S3-compatible test fixture. MinIO withdrew its public images in September 2026
 * (Docker Hub deleted, quay.io requires login), so the compatibility fixture is
 * RustFS 1.0.3 (Apache-2.0, MinIO-compatible API), pinned by digest. It enforces
 * the configured credentials on port 9000; production storage remains an operator choice.
 */
public final class StorageImage {
    // Testcontainers parses digest references without a simultaneous version tag.
    public static final DockerImageName RUSTFS = DockerImageName.parse(
            "rustfs/rustfs@sha256:9ca9559533b537d10482fe923288b676ad08f92a7069d9a17327fbae3cab688b");
    public static final int PORT = 9000;

    public static GenericContainer<?> container(String accessKey, String secretKey) {
        return new GenericContainer<>(RUSTFS)
                .withEnv("RUSTFS_ACCESS_KEY", accessKey).withEnv("RUSTFS_SECRET_KEY", secretKey)
                .withEnv("RUSTFS_VOLUMES", "/data").withEnv("RUSTFS_ADDRESS", "0.0.0.0:" + PORT)
                .withEnv("RUSTFS_CONSOLE_ENABLE", "false")
                // The strict disk topology check rejects the container's overlay filesystem; local testing only.
                .withEnv("RUSTFS_UNSAFE_BYPASS_DISK_CHECK", "true")
                .withExposedPorts(PORT)
                .waitingFor(Wait.forHttp("/health").forPort(PORT));
    }

    private StorageImage() {}
}
