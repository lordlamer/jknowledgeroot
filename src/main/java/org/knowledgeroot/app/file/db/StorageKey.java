package org.knowledgeroot.app.file.db;

final class StorageKey {
    private StorageKey() {}
    static String validate(String key) {
        if (key == null || !key.matches("(?:[0-9a-f]{32}|sha256-[0-9a-f]{64})")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return key;
    }
}
