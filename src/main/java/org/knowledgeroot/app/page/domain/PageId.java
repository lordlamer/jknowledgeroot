package org.knowledgeroot.app.page.domain;

public record PageId(Integer value) {
    public PageId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
