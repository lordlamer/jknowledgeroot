package org.knowledgeroot.test;

import org.testcontainers.utility.DockerImageName;

/** MariaDB 12.3.3 LTS: same immutable image as the production Compose default. */
public final class DatabaseImage {
    // Testcontainers parses digest references without a simultaneous version tag.
    public static final DockerImageName MARIADB = DockerImageName.parse(
            "mariadb@sha256:ab1c3dd381940233af12512b97d47b508fd3a0f17fbe3ba388739b7bc17cbc0b");

    private DatabaseImage() {}
}
