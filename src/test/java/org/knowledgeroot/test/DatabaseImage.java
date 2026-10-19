package org.knowledgeroot.test;

import org.testcontainers.utility.DockerImageName;

/** MariaDB 12.3.3 LTS, digest of 17 October 2026: same immutable image as deploy/Dockerfile.database. */
public final class DatabaseImage {
    // Testcontainers parses digest references without a simultaneous version tag.
    public static final DockerImageName MARIADB = DockerImageName.parse(
            "mariadb@sha256:99c125f103f5d081ebbc9ffe064e63daf7f688ac33a5f7f29af2d7260a96debf");

    private DatabaseImage() {}
}
