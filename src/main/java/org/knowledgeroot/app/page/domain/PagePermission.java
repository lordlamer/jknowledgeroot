package org.knowledgeroot.app.page.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagePermission {
    private Integer id;
    private PageId pageId;
    private RoleType roleType;
    private Integer roleId;  // null für Gäste
    private PermissionLevel permissionLevel;
    private Integer createdBy;
    private LocalDateTime createDate;
    private Integer changedBy;
    private LocalDateTime changeDate;

    // Nutzerfreundlicher Anzeigename (für UI)
    private String roleName;

    public enum RoleType {
        USER("user"),
        GROUP("group"),
        GUEST("guest");

        private final String value;

        RoleType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static RoleType fromString(String value) {
            for (RoleType type : RoleType.values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown role type: " + value);
        }
    }

    public enum PermissionLevel {
        NONE("none"),
        VIEW("view"),
        EDIT("edit");

        private final String value;

        PermissionLevel(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static PermissionLevel fromString(String value) {
            for (PermissionLevel level : PermissionLevel.values()) {
                if (level.value.equalsIgnoreCase(value)) {
                    return level;
                }
            }
            throw new IllegalArgumentException("Unknown permission level: " + value);
        }
    }
}