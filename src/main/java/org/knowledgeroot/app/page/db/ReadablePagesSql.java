package org.knowledgeroot.app.page.db;

/** Set-based equivalent of PagePermissionImpl's VIEW policy, before filtering/pagination. */
final class ReadablePagesSql {
    private ReadablePagesSql() {}

    static final String CTE = """
            WITH RECURSIVE permission_tree (page_id, source_id) AS (
                SELECT id, id FROM page WHERE deleted = 0 AND inherit_permissions = 0
                UNION ALL
                SELECT child.id, tree.source_id FROM page child
                JOIN permission_tree tree ON child.parent = tree.page_id
                WHERE child.deleted = 0 AND child.inherit_permissions = 1
            ), current_actor AS (
                SELECT id, admin FROM user WHERE id = :viewer AND active = 1 AND deleted = 0
            ), readable_pages AS (
                SELECT p.id FROM page p
                LEFT JOIN permission_tree tree ON tree.page_id = p.id
                WHERE p.deleted = 0 AND (
                    EXISTS (SELECT 1 FROM current_actor WHERE admin = 1)
                    OR ((:viewer IS NULL OR EXISTS (SELECT 1 FROM current_actor)) AND EXISTS (
                        SELECT 1 FROM page_permission grant_row
                        WHERE grant_row.page_id = tree.source_id AND grant_row.permission_level IN ('view', 'edit')
                        AND (grant_row.role_type = 'guest'
                            OR (grant_row.role_type = 'user' AND grant_row.role_id = :viewer)
                            OR (grant_row.role_type = 'group' AND EXISTS (
                                SELECT 1 FROM group_member membership
                                WHERE membership.group_id = grant_row.role_id
                                  AND membership.member_type = 'user' AND membership.member_id = :viewer
                            )))
                    ))
                )
            )
            """;
}
