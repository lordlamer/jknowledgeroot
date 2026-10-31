package org.knowledgeroot.app.space;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Spaces, their permissions and the global grants ({@code space}, {@code space_permission}, {@code global_permission}). */
@Repository
@RequiredArgsConstructor
public class SpaceRepository {
    public static final String CREATE_SPACE = "create-space";
    private static final String COLUMNS = "id, space_key, name, description, home_page_id, archived, created_by, create_date, changed_by, change_date";
    private final JdbcClient jdbc;

    private static Space space(ResultSet rs, int row) throws SQLException {
        return Space.builder().id(rs.getInt("id")).key(rs.getString("space_key")).name(rs.getString("name"))
                .description(rs.getString("description")).homePageId(rs.getObject("home_page_id", Integer.class))
                .archived(rs.getBoolean("archived")).createdBy(rs.getObject("created_by", Integer.class))
                .createDate(rs.getTimestamp("create_date").toLocalDateTime()).changedBy(rs.getObject("changed_by", Integer.class))
                .changeDate(rs.getTimestamp("change_date").toLocalDateTime()).build();
    }

    public List<Space> listAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM space ORDER BY archived, name").query(SpaceRepository::space).list();
    }

    /** Spaces the viewer may enter, active ones first; administrators get every space. */
    public List<Space> listVisible(Integer viewer) {
        return jdbc.sql("WITH " + SpaceSql.CURRENT_ACTOR + ", " + SpaceSql.VISIBLE_SPACES
                        + "SELECT " + COLUMNS + " FROM space WHERE id IN (SELECT id FROM visible_spaces) ORDER BY archived, name")
                .param("viewer", viewer).query(SpaceRepository::space).list();
    }

    public Optional<Space> find(int id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM space WHERE id = :id").param("id", id).query(SpaceRepository::space).optional();
    }

    public Optional<Space> findByKey(String key) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM space WHERE space_key = :key").param("key", key).query(SpaceRepository::space).optional();
    }

    /** The oldest space; takes pages whose space is not named (REST API, demo data). */
    public Space defaultSpace() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM space ORDER BY id LIMIT 1").query(SpaceRepository::space).single();
    }

    public Optional<Integer> spaceOfPage(int pageId) {
        return jdbc.sql("SELECT space_id FROM page WHERE id = :id").param("id", pageId).query(Integer.class).optional();
    }

    public int create(Space space) {
        var keys = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbc.sql("INSERT INTO space (space_key, name, description, created_by, create_date, changed_by, change_date) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(space.getKey(), space.getName(), space.getDescription(), space.getCreatedBy(), now, space.getCreatedBy(), now)
                .update(keys);
        return keys.getKey().intValue();
    }

    public void update(Space space) {
        jdbc.sql("UPDATE space SET name = ?, description = ?, home_page_id = ?, archived = ?, changed_by = ?, change_date = ? WHERE id = ?")
                .params(space.getName(), space.getDescription(), space.getHomePageId(), space.isArchived(),
                        space.getChangedBy(), LocalDateTime.now(), space.getId()).update();
    }

    public boolean isArchived(int spaceId) {
        return jdbc.sql("SELECT archived FROM space WHERE id = :id").param("id", spaceId).query(Boolean.class).optional().orElse(false);
    }

    /** The viewer's strongest grant on the space, empty without any; administrators are the caller's business. */
    public Optional<SpacePermission.Level> level(int spaceId, Integer viewer) {
        Integer rank = jdbc.sql("""
                SELECT MAX(CASE sp.permission_level WHEN 'admin' THEN 3 WHEN 'edit' THEN 2 ELSE 1 END)
                FROM space_permission sp WHERE sp.space_id = :space AND """ + SpaceSql.GRANT)
                .param("space", spaceId).param("viewer", viewer).query(Integer.class).optional().orElse(null);
        if (rank == null) return Optional.empty();
        return Optional.of(rank == 3 ? SpacePermission.Level.ADMIN : rank == 2 ? SpacePermission.Level.EDIT : SpacePermission.Level.VIEW);
    }

    public List<SpacePermission> listPermissions(int spaceId) {
        return jdbc.sql("""
                SELECT sp.id, sp.space_id, sp.role_type, sp.role_id, sp.permission_level, sp.created_by, sp.create_date,
                       sp.changed_by, sp.change_date,
                       CASE sp.role_type WHEN 'user' THEN COALESCE(NULLIF(TRIM(CONCAT(u.first_name, ' ', u.last_name)), ''), u.login)
                                         WHEN 'group' THEN g.name WHEN 'guest' THEN 'Guests' ELSE 'All signed-in users' END AS role_name
                FROM space_permission sp
                LEFT JOIN user u ON sp.role_type = 'user' AND u.id = sp.role_id
                LEFT JOIN `group` g ON sp.role_type = 'group' AND g.id = sp.role_id
                WHERE sp.space_id = :space ORDER BY sp.role_type, role_name
                """).param("space", spaceId).query((rs, row) -> SpacePermission.builder()
                .id(rs.getInt("id")).spaceId(rs.getInt("space_id")).role(SpacePermission.Role.fromValue(rs.getString("role_type")))
                .roleId(rs.getObject("role_id", Integer.class)).level(SpacePermission.Level.fromValue(rs.getString("permission_level")))
                .roleName(rs.getString("role_name")).createdBy(rs.getObject("created_by", Integer.class))
                .createDate(rs.getTimestamp("create_date").toLocalDateTime()).changedBy(rs.getObject("changed_by", Integer.class))
                .changeDate(rs.getTimestamp("change_date").toLocalDateTime()).build()).list();
    }

    public boolean hasGrant(int spaceId, SpacePermission.Role role, Integer roleId) {
        return jdbc.sql("SELECT COUNT(*) FROM space_permission WHERE space_id = :space AND role_type = :role AND (role_id = :id OR (:id IS NULL AND role_id IS NULL))")
                .param("space", spaceId).param("role", role.getValue()).param("id", roleId).query(Integer.class).single() > 0;
    }

    public void createPermission(int spaceId, SpacePermission.Role role, Integer roleId, SpacePermission.Level level, Integer actor) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.sql("INSERT INTO space_permission (space_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(spaceId, role.getValue(), roleId, level.getValue(), actor, now, actor, now).update();
    }

    public boolean updatePermission(int spaceId, int permissionId, SpacePermission.Level level, Integer actor) {
        return jdbc.sql("UPDATE space_permission SET permission_level = ?, changed_by = ?, change_date = ? WHERE id = ? AND space_id = ?")
                .params(level.getValue(), actor, LocalDateTime.now(), permissionId, spaceId).update() == 1;
    }

    public boolean deletePermission(int spaceId, int permissionId) {
        return jdbc.sql("DELETE FROM space_permission WHERE id = ? AND space_id = ?").params(permissionId, spaceId).update() == 1;
    }

    // --- global permissions --------------------------------------------------------------------

    public record GlobalGrant(int id, String permission, SpacePermission.Role role, Integer roleId, String roleName) {}

    public boolean hasGlobal(String permission, Integer viewer) {
        if (viewer == null) return false;
        return jdbc.sql("""
                SELECT COUNT(*) FROM global_permission sp WHERE sp.permission = :permission AND """ + SpaceSql.GRANT)
                .param("permission", permission).param("viewer", viewer).query(Integer.class).single() > 0;
    }

    public List<GlobalGrant> listGlobal(String permission) {
        return jdbc.sql("""
                SELECT gp.id, gp.permission, gp.role_type, gp.role_id,
                       CASE gp.role_type WHEN 'user' THEN COALESCE(NULLIF(TRIM(CONCAT(u.first_name, ' ', u.last_name)), ''), u.login)
                                         WHEN 'group' THEN g.name ELSE 'All signed-in users' END AS role_name
                FROM global_permission gp
                LEFT JOIN user u ON gp.role_type = 'user' AND u.id = gp.role_id
                LEFT JOIN `group` g ON gp.role_type = 'group' AND g.id = gp.role_id
                WHERE gp.permission = :permission ORDER BY gp.role_type, role_name
                """).param("permission", permission).query((rs, row) -> new GlobalGrant(rs.getInt("id"), rs.getString("permission"),
                SpacePermission.Role.fromValue(rs.getString("role_type")), rs.getObject("role_id", Integer.class), rs.getString("role_name"))).list();
    }

    @Transactional
    public boolean grantGlobal(String permission, SpacePermission.Role role, Integer roleId, Integer actor) {
        boolean exists = jdbc.sql("SELECT COUNT(*) FROM global_permission WHERE permission = :p AND role_type = :r AND (role_id = :id OR (:id IS NULL AND role_id IS NULL))")
                .param("p", permission).param("r", role.getValue()).param("id", roleId).query(Integer.class).single() > 0;
        if (exists) return false;
        jdbc.sql("INSERT INTO global_permission (permission, role_type, role_id, created_by, create_date) VALUES (?, ?, ?, ?, ?)")
                .params(permission, role.getValue(), roleId, actor, LocalDateTime.now()).update();
        return true;
    }

    public boolean revokeGlobal(int id) {
        return jdbc.sql("DELETE FROM global_permission WHERE id = ?").param(id).update() == 1;
    }
}
