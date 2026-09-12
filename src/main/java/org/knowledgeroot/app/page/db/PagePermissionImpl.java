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
import java.util.HashSet;

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
                                .createdBy(rs.getObject("created_by", Integer.class))
                                .createDate(rs.getTimestamp("create_date").toLocalDateTime())
                                .changedBy(rs.getObject("changed_by", Integer.class))
                                .changeDate(rs.getTimestamp("change_date").toLocalDateTime())
                                .roleName(rs.getString("role_name"))
                                .build()
                )
                .list();
    }

    @Override
    public boolean hasUserPermission(PageId pageId, Integer userId, PagePermission.PermissionLevel minPermissionLevel) {
        if (node(pageId) == null) {
            return false;
        }
        if (userId != null) {
            Boolean isAdmin = jdbcClient.sql("SELECT admin FROM user WHERE id = :id AND active = 1 AND deleted = 0")
                    .param("id", userId).query(Boolean.class).optional().orElse(null);
            if (isAdmin == null) {
                return false;
            }
            if (isAdmin) {
                return true;
            }
        }
        pageId = permissionSource(pageId);
        if (pageId == null) {
            return false;
        }
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
        requireLocal(pagePermission.getPageId());
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
    public void updatePermissionForPage(PageId pageId, PagePermission pagePermission) {
        requireLocal(pageId);
        int update = jdbcClient.sql("""
                UPDATE page_permission SET
                    permission_level = ?,
                    changed_by = ?,
                    change_date = ?
                WHERE
                    id = ?
                    AND page_id = ?
                """)
                .params(
                        pagePermission.getPermissionLevel().getValue(),
                        pagePermission.getChangedBy(),
                        pagePermission.getChangeDate(),
                        pagePermission.getId(),
                        pageId.value()
                )
                .update();

        if (update != 1) throw new org.springframework.dao.EmptyResultDataAccessException(1);
    }

    @Override
    public void deletePermission(Integer permissionId) {
        jdbcClient.sql("DELETE FROM page_permission WHERE id = :id")
                .param("id", permissionId)
                .update();
    }

    @Override
    public void deletePermissionForPage(PageId pageId, Integer permissionId) {
        requireLocal(pageId);
        int deleted = jdbcClient.sql("""
                DELETE FROM page_permission
                WHERE id = :id
                AND page_id = :pageId
                """)
                .param("id", permissionId)
                .param("pageId", pageId.value())
                .update();

        if (deleted != 1) throw new org.springframework.dao.EmptyResultDataAccessException(1);
    }

    @Override
    public void createDefaultPermissions(PageId pageId, Integer creatorId) {
        PermissionNode page = node(pageId);
        Assert.state(page != null, "Page does not exist");
        if (page.parent() > 0) {
            Assert.state(permissionSource(new PageId(page.parent())) != null, "Invalid parent hierarchy");
            jdbcClient.sql("UPDATE page SET inherit_permissions = TRUE WHERE id = :id")
                    .param("id", pageId.value()).update();
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        // 1. Creator gets EDIT permissions (only if creatorId is not null)
        if (creatorId != null) {
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
        }

        // A guest-created root is public to read; authenticated roots stay private.
        if (creatorId != null) {
            return;
        }
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

    private record PermissionNode(int parent, boolean inherits) {}

    private PermissionNode node(PageId pageId) {
        return jdbcClient.sql("SELECT parent, inherit_permissions FROM page WHERE id = :id AND deleted = 0")
                .param("id", pageId.value())
                .query((rs, row) -> new PermissionNode(rs.getInt("parent"), rs.getBoolean("inherit_permissions")))
                .optional().orElse(null);
    }

    @Override
    public PageId permissionSource(PageId pageId) {
        var visited = new HashSet<Integer>();
        while (pageId != null && visited.add(pageId.value())) {
            PermissionNode page = node(pageId);
            if (page == null) return null;
            if (!page.inherits()) return pageId;
            pageId = page.parent() > 0 ? new PageId(page.parent()) : null;
        }
        return null;
    }

    @Override
    public boolean isInheriting(PageId pageId) {
        PermissionNode page = node(pageId);
        Assert.state(page != null, "Page does not exist");
        return page.inherits();
    }

    private void requireLocal(PageId pageId) {
        jdbcClient.sql("SELECT id FROM page WHERE id = :id FOR UPDATE")
                .param("id", pageId.value()).query(Integer.class).single();
        Assert.state(!isInheriting(pageId), "Switch to local permissions before editing grants");
    }

    @Override
    public void setInheriting(PageId pageId, boolean inherit, Integer actorId) {
        // Serialize mode changes on the target page. This method runs transactionally.
        jdbcClient.sql("SELECT id FROM page WHERE id = :id FOR UPDATE")
                .param("id", pageId.value()).query(Integer.class).single();
        PermissionNode page = node(pageId);
        Assert.state(page != null, "Page does not exist");
        if (page.inherits() == inherit) return;
        PageId source = inherit && page.parent() > 0
                ? permissionSource(new PageId(page.parent())) : permissionSource(pageId);
        Assert.state(source != null && (!inherit || (page.parent() > 0 && !source.equals(pageId))),
                "Cannot inherit from an invalid parent hierarchy");
        jdbcClient.sql("DELETE FROM page_permission WHERE page_id = :id").param("id", pageId.value()).update();
        if (!inherit) {
            jdbcClient.sql("""
                    INSERT INTO page_permission (page_id, role_type, role_id, permission_level,
                                                 created_by, create_date, changed_by, change_date)
                    SELECT :id, role_type, role_id, permission_level, :actor, :now, :actor, :now
                    FROM page_permission WHERE page_id = :source
                    """)
                    .param("id", pageId.value()).param("actor", actorId)
                    .param("now", LocalDateTime.now()).param("source", source.value()).update();
        }
        jdbcClient.sql("UPDATE page SET inherit_permissions = :inherit, changed_by = :actor, change_date = :now WHERE id = :id")
                .param("inherit", inherit).param("actor", actorId).param("now", LocalDateTime.now())
                .param("id", pageId.value()).update();
    }
}
