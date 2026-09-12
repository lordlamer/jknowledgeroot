package org.knowledgeroot.app.security.context.domain;

public interface UserContextDao {
    UserDetails getUserDetails(String name);

    /** Find an active, non-deleted account by its stable ID. */
    UserDetails getUserDetailsById(String userId);
}
