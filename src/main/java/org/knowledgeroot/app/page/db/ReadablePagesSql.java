package org.knowledgeroot.app.page.db;

import org.knowledgeroot.app.space.SpaceSql;

/**
 * Set-based equivalent of PagePermissionImpl's VIEW policy, before filtering/pagination: the
 * viewer must be able to enter the page's space, and the nearest page with local permissions
 * (or the space itself for root pages that inherit from it) must grant view or edit.
 */
final class ReadablePagesSql {
    private ReadablePagesSql() {}

    static final String CTE = "WITH RECURSIVE " + SpaceSql.CURRENT_ACTOR + ", " + SpaceSql.VISIBLE_SPACES + """
            , permission_tree (page_id, source_id, from_space) AS (
                SELECT id, id, 0 FROM page WHERE deleted = 0 AND inherit_permissions = 0
                UNION ALL
                SELECT id, NULL, 1 FROM page WHERE deleted = 0 AND inherit_permissions = 1 AND parent = 0
                UNION ALL
                SELECT child.id, tree.source_id, tree.from_space FROM page child
                JOIN permission_tree tree ON child.parent = tree.page_id
                WHERE child.deleted = 0 AND child.inherit_permissions = 1 AND child.parent > 0
            ), readable_pages AS (
                SELECT p.id FROM page p
                LEFT JOIN permission_tree tree ON tree.page_id = p.id
                WHERE p.deleted = 0 AND p.space_id IN (SELECT id FROM visible_spaces) AND (
                    EXISTS (SELECT 1 FROM current_actor WHERE admin = 1)
                    OR tree.from_space = 1
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
