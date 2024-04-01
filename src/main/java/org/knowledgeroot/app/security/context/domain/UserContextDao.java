package org.knowledgeroot.app.security.context.domain;

public interface UserContextDao {
    UserDetails getUserDetails(String name);
}
