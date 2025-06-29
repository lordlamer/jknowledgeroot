package org.knowledgeroot.app.page.db;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class PagePermissionImpl implements PagePermissionDao {
    private final JdbcClient jdbcClient;

    @Override
    public List<PagePermission> listPermissionsByPageId(PageId pageId) {
        return jdbcClient.sql("""
                SELECT 
                    pp.id,
                    pp.page_id,
                    pp.role_type,
                    pp.role_id,
                    pp.permission_level,
                    pp.created_by,
                    pp.create_date,
                    pp.changed_by,
                    pp.change_date,
                    CASE 
                        WHEN pp.role_type = 'user' THEN CONCAT(u.first_name, ' ', u.last_name)
                        WHEN pp.role_type = 'group' THEN g.name
                        WHEN pp.role_type = 'guest' THEN 'Guest'
                    END as role_name
                FROM 
                    page_permission pp
                LEFT JOIN 
                    `user` u ON pp.role_type = 'user' AND pp.role_id = u.id
                LEFT JOIN 
                    `group` g ON pp.role_type = 'group' AND pp.role_id = g.id
                WHERE 
                    pp.page_id = :pageId
                ORDER BY
                    pp.role_type, role_name
                """)
                .param("pageId", pageId.value())
                .query((rs, rowNum) ->
                        PagePermission.builder()
                                .id(rs.getInt("id"))
                                .pageId(new PageId(rs.getInt("page_id")))
                                .roleType(PagePermission.RoleType.fromString(rs.getString("role_type")))
                                .roleId(rs.getObject("role_id") != null ? rs.getInt("role_id") : null)
                                .permissionLevel(PagePermission.PermissionLevel.fromString(rs.getString("permission_level")))
                                .createdBy(rs.getInt("created_by"))
                                .createDate(rs.getTimestamp("create_date").toLocalDateTime())
                                .changedBy(rs.getInt("changed_by"))
                                .changeDate(rs.getTimestamp("change_date").toLocalDateTime())
                                .roleName(rs.getString("role_name"))
                                .build()
                )
                .list();
    }

    @Override
    public boolean hasUserPermission(PageId pageId, Integer userId, PagePermission.PermissionLevel minPermissionLevel) {
        // Special handling for guest users (userId = null means guest)
        if (userId == null) {
            return hasGuestPermission(pageId, minPermissionLevel);
        }

        // 1. Check direct user permissions
        Integer directCount = jdbcClient.sql("""
                SELECT COUNT(*) FROM page_permission
                WHERE page_id = :pageId
                AND role_type = 'user'
                AND role_id = :userId
                AND CASE
                    WHEN :minLevel = 'edit' THEN permission_level = 'edit'
                    WHEN :minLevel = 'view' THEN permission_level IN ('view', 'edit')
                    ELSE 0
                END
                """)
                .param("pageId", pageId.value())
                .param("userId", userId)
                .param("minLevel", minPermissionLevel.getValue())
                .query(Integer.class)
                .single();

        if (directCount > 0) {
            return true;
        }

        // 2. Check group permissions (find groups user is a member of)
        Integer groupCount = jdbcClient.sql("""
                SELECT COUNT(*) FROM page_permission pp
                JOIN group_member gm ON pp.role_type = 'group' AND pp.role_id = gm.group_id
                WHERE pp.page_id = :pageId
                AND gm.member_type = 'user'
                AND gm.member_id = :userId
                AND CASE
                    WHEN :minLevel = 'edit' THEN pp.permission_level = 'edit'
                    WHEN :minLevel = 'view' THEN pp.permission_level IN ('view', 'edit')
                    ELSE 0
                END
                """)
                .param("pageId", pageId.value())
                .param("userId", userId)
                .param("minLevel", minPermissionLevel.getValue())
                .query(Integer.class)
                .single();

        if (groupCount > 0) {
            return true;
        }

        // 3. Fall back to guest permissions if no user/group permissions found
        return hasGuestPermission(pageId, minPermissionLevel);
    }

    /**
     * Check if guest users have specific permission for a page
     * @param pageId The page ID
     * @param minPermissionLevel Minimum required permission level
     * @return true if guests have permission, false otherwise
     */
    private boolean hasGuestPermission(PageId pageId, PagePermission.PermissionLevel minPermissionLevel) {
        Integer guestCount = jdbcClient.sql("""
                SELECT COUNT(*) FROM page_permission
                WHERE page_id = :pageId
                AND role_type = 'guest'
                AND role_id IS NULL
                AND CASE
                    WHEN :minLevel = 'edit' THEN permission_level = 'edit'
                    WHEN :minLevel = 'view' THEN permission_level IN ('view', 'edit')
                    ELSE 0
                END
                """)
                .param("pageId", pageId.value())
                .param("minLevel", minPermissionLevel.getValue())
                .query(Integer.class)
                .single();

        return guestCount > 0;
    }

    @Override
    public void createPermission(PagePermission pagePermission) {
        int update = jdbcClient.sql("""
                INSERT INTO page_permission (
                    page_id,
                    role_type,
                    role_id,
                    permission_level,
                    created_by,
                    create_date,
                    changed_by,
                    change_date
                ) VALUES (
                    ?,?,?,?,?,?,?,?
                )
                """)
                .params(
                        pagePermission.getPageId().value(),
                        pagePermission.getRoleType().getValue(),
                        pagePermission.getRoleId(),
                        pagePermission.getPermissionLevel().getValue(),
                        pagePermission.getCreatedBy(),
                        pagePermission.getCreateDate(),
                        pagePermission.getChangedBy(),
                        pagePermission.getChangeDate()
                )
                .update();

        Assert.state(update == 1, "Failed to create permission for page: " + pagePermission.getPageId().value());
    }

    @Override
    public void updatePermission(PagePermission pagePermission) {
        int update = jdbcClient.sql("""
                UPDATE page_permission SET
                    permission_level = ?,
                    changed_by = ?,
                    change_date = ?
                WHERE
                    id = ?
                """)
                .params(
                        pagePermission.getPermissionLevel().getValue(),
                        pagePermission.getChangedBy(),
                        pagePermission.getChangeDate(),
                        pagePermission.getId()
                )
                .update();

        Assert.state(update == 1, "Failed to update permission with ID: " + pagePermission.getId());
    }

    @Override
    public void deletePermission(Integer permissionId) {
        jdbcClient.sql("DELETE FROM page_permission WHERE id = :id")
                .param("id", permissionId)
                .update();
    }

    @Override
    public void createDefaultPermissions(PageId pageId, Integer creatorId) {
        LocalDateTime now = LocalDateTime.now();

        // 1. Creator gets EDIT permissions
        jdbcClient.sql("""
                INSERT INTO page_permission (
                    page_id,
                    role_type,
                    role_id,
                    permission_level,
                    created_by,
                    create_date,
                    changed_by,
                    change_date
                ) VALUES (
                    ?,?,?,?,?,?,?,?
                )
                """)
                .params(
                        pageId.value(),
                        PagePermission.RoleType.USER.getValue(),
                        creatorId,
                        PagePermission.PermissionLevel.EDIT.getValue(),
                        creatorId,
                        now,
                        creatorId,
                        now
                )
                .update();

        // 2. Guest gets VIEW permissions
        jdbcClient.sql("""
                INSERT INTO page_permission (
                    page_id,
                    role_type,
                    role_id,
                    permission_level,
                    created_by,
                    create_date,
                    changed_by,
                    change_date
                ) VALUES (
                    ?,?,?,?,?,?,?,?
                )
                """)
                .params(
                        pageId.value(),
                        PagePermission.RoleType.GUEST.getValue(),
                        null,
                        PagePermission.PermissionLevel.VIEW.getValue(),
                        creatorId,
                        now,
                        creatorId,
                        now
                )
                .update();
    }
}
