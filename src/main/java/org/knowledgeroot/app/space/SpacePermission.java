package org.knowledgeroot.app.space;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/** A grant on a space for a user, a group, guests or every signed-in user. */
@Getter
@Builder
public class SpacePermission {
    private Integer id;
    private int spaceId;
    private Role role;
    private Integer roleId;
    private Level level;
    private String roleName;
    private Integer createdBy;
    private LocalDateTime createDate;
    private Integer changedBy;
    private LocalDateTime changeDate;

    @Getter
    @RequiredArgsConstructor
    public enum Role {
        USER("user"), GROUP("group"), GUEST("guest"),
        /** Every signed-in user. */
        AUTHENTICATED("users");

        private final String value;

        public boolean needsId() {
            return this == USER || this == GROUP;
        }

        public static Role fromValue(String value) {
            for (Role role : values()) if (role.value.equalsIgnoreCase(value)) return role;
            throw new IllegalArgumentException("Unknown role type");
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum Level {
        /** See the space and read pages the page permissions allow. */
        VIEW("view"),
        /** Additionally create root pages. */
        EDIT("edit"),
        /** Additionally manage name, home page, permissions and archiving. */
        ADMIN("admin");

        private final String value;

        public boolean covers(Level required) {
            return ordinal() >= required.ordinal();
        }

        public static Level fromValue(String value) {
            for (Level level : values()) if (level.value.equalsIgnoreCase(value)) return level;
            throw new IllegalArgumentException("Unknown permission level");
        }
    }
}
