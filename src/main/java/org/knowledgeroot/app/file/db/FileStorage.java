package org.knowledgeroot.app.file.db;

import java.io.InputStream;

public interface FileStorage {
    void store(String hash, InputStream inputStream);
    InputStream retrieve(String hash);
    void delete(String hash);
    boolean exists(String hash);
}
