package org.knowledgeroot.app.file.domain;

public class StoredFileNotFoundException extends StorageException {
    public StoredFileNotFoundException(Throwable cause) { super("Stored file not found", cause); }
}
