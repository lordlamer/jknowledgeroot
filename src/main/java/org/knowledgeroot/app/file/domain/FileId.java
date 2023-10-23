package org.knowledgeroot.app.file.domain;

public record FileId(Integer value) {
    public FileId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
