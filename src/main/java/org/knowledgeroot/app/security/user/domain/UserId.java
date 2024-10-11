package org.knowledgeroot.app.security.user.domain;

public record UserId(Integer value) {
    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
