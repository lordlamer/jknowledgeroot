package org.knowledgeroot.app.space;

/** SQL fragments shared by the space repository and the readable-pages query. Parameter: {@code :viewer}. */
public final class SpaceSql {
    private SpaceSql() {}

    /** Whether a space_permission row aliased {@code sp} applies to {@code :viewer} (null = guest). */
    public static final String GRANT = """
            (sp.role_type = 'guest'
                OR (sp.role_type = 'users' AND :viewer IS NOT NULL)
                OR (sp.role_type = 'user' AND sp.role_id = :viewer)
                OR (sp.role_type = 'group' AND EXISTS (
                    SELECT 1 FROM group_member m
                    WHERE m.group_id = sp.role_id AND m.member_type = 'user' AND m.member_id = :viewer)))
            """;

    /** The active account behind {@code :viewer}; empty for guests and disabled or deleted accounts. */
    public static final String CURRENT_ACTOR = """
            current_actor AS (
                SELECT id, admin FROM user WHERE id = :viewer AND active = 1 AND deleted = 0
            )
            """;

    /**
     * Spaces the viewer may enter: administrators see all, others need a grant on an active space;
     * archived spaces stay visible (read-only) to their space administrators. Needs {@link #CURRENT_ACTOR}.
     */
    public static final String VISIBLE_SPACES = """
            visible_spaces AS (
                SELECT s.id FROM space s
                WHERE EXISTS (SELECT 1 FROM current_actor WHERE admin = 1)
                   OR (s.archived = 0 AND (:viewer IS NULL OR EXISTS (SELECT 1 FROM current_actor)) AND EXISTS (
                        SELECT 1 FROM space_permission sp WHERE sp.space_id = s.id AND """ + GRANT + """
                   ))
                   OR (s.archived = 1 AND EXISTS (SELECT 1 FROM current_actor) AND EXISTS (
                        SELECT 1 FROM space_permission sp WHERE sp.space_id = s.id AND sp.permission_level = 'admin' AND """ + GRANT + """
                   ))
            )
            """;
}
