package org.knowledgeroot.app.security.context.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Getter
@Builder
public class UserDetails implements Serializable {
    /**
     * Role of the user.
     */
    @Getter
    @RequiredArgsConstructor
    public enum Role {
        ADMIN("ROLE_ADMIN"),
        USER("ROLE_USER"),
        GUEST("ROLE_GUEST"),
        ;

        private final String value;

        @Override
        public String toString() {
            return super.toString();
        }
    }

    // user details
    private String userId;
    private String login;
    private String email;
    private String firstName;
    private String surName;
    private Role role;
    private String language;

    /**
     * Get the full name of the user.
     * @return full name
     */
    public String getFullName() {
        return firstName + " " + surName;
    }

    /**
     * Check if the user is an admin.
     * @return true if the user is an admin
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * Check if the user is a user.
     * @return true if the user is a user
     */
    public boolean isUser() {
        return role == Role.USER;
    }

    /**
     * Check if the user is a guest.
     * @return true if the user is a guest
     */
    public boolean isGuest() {
        return role == Role.GUEST;
    }
}
