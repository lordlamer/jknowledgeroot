package org.knowledgeroot.app.content.domain;

public record ContentId(Integer value) {
    public ContentId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
