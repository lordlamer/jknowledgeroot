package org.knowledgeroot.app.security.user.domain;

public record GroupId(Integer value) {
    public GroupId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
