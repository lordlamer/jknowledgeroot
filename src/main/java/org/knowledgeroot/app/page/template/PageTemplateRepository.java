package org.knowledgeroot.app.page.template;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The {@code page_template} table; labels are stored one per line like the working copy's. */
@Repository
@RequiredArgsConstructor
public class PageTemplateRepository {
    private static final String LABEL_SEPARATOR = "\n";
    private static final String SELECT = """
            SELECT t.id, t.space_id, t.name, t.content, t.labels, t.created_by, t.create_date, t.changed_by,
                   u.login AS changed_by_login, t.change_date
            FROM page_template t LEFT JOIN `user` u ON u.id = t.changed_by
            """;
    private final JdbcClient jdbc;

    private static PageTemplate template(ResultSet rs, int row) throws SQLException {
        return new PageTemplate(rs.getInt("id"), rs.getInt("space_id"), rs.getString("name"), rs.getString("content"),
                labels(rs.getString("labels")), rs.getObject("created_by", Integer.class), rs.getTimestamp("create_date").toLocalDateTime(),
                rs.getObject("changed_by", Integer.class), rs.getString("changed_by_login"), rs.getTimestamp("change_date").toLocalDateTime());
    }

    public List<PageTemplate> listForSpace(int spaceId) {
        return jdbc.sql(SELECT + "WHERE t.space_id = :space ORDER BY t.name").param("space", spaceId).query(PageTemplateRepository::template).list();
    }

    /** Every template of every space, by space and name; for the MCP prompts. */
    public List<PageTemplate> listAll() {
        return jdbc.sql(SELECT + "ORDER BY t.space_id, t.name").query(PageTemplateRepository::template).list();
    }

    public Optional<PageTemplate> find(int id) {
        return jdbc.sql(SELECT + "WHERE t.id = :id").param("id", id).query(PageTemplateRepository::template).optional();
    }

    public int countForSpace(int spaceId) {
        return jdbc.sql("SELECT COUNT(*) FROM page_template WHERE space_id = :space").param("space", spaceId).query(Integer.class).single();
    }

    public boolean nameTaken(int spaceId, String name, Integer exceptId) {
        return jdbc.sql("SELECT COUNT(*) FROM page_template WHERE space_id = :space AND name = :name AND (:except IS NULL OR id <> :except)")
                .param("space", spaceId).param("name", name).param("except", exceptId).query(Integer.class).single() > 0;
    }

    public int create(int spaceId, String name, String content, List<String> labels, Integer actor) {
        var keys = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbc.sql("INSERT INTO page_template (space_id, name, content, labels, created_by, create_date, changed_by, change_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(spaceId, name, content, String.join(LABEL_SEPARATOR, labels), actor, now, actor, now).update(keys);
        return keys.getKey().intValue();
    }

    public void update(int id, String name, String content, List<String> labels, Integer actor) {
        jdbc.sql("UPDATE page_template SET name = ?, content = ?, labels = ?, changed_by = ?, change_date = ? WHERE id = ?")
                .params(name, content, String.join(LABEL_SEPARATOR, labels), actor, LocalDateTime.now(), id).update();
    }

    public boolean delete(int id) {
        return jdbc.sql("DELETE FROM page_template WHERE id = ?").param(id).update() == 1;
    }

    private static List<String> labels(String stored) {
        if (stored == null || stored.isEmpty()) return List.of();
        return Arrays.stream(stored.split(LABEL_SEPARATOR)).filter(label -> !label.isBlank()).toList();
    }
}
