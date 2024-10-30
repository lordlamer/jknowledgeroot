package org.knowledgeroot.app.file.db;

import java.io.InputStream;

/**
 * File storage interface for storing and retrieving files.
 */
interface FileStorage {
    void store(String hash, InputStream inputStream);
    InputStream retrieve(String hash);
    void delete(String hash);
    boolean exists(String hash);
}
