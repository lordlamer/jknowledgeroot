package org.knowledgeroot.app.page.domain;

public record PageId(Integer value) {
    public PageId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Page ID must be positive");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
