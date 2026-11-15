package org.knowledgeroot.app.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Who watches which page or space ({@code page_watch}, {@code space_watch}). */
@Repository
@RequiredArgsConstructor
public class WatchRepository {
    private final JdbcClient jdbc;

    public record WatchedPage(int id, String name, int spaceId) {}

    public record WatchedSpace(int id, String key, String name) {}

    public void watchPage(int userId, int pageId) {
        jdbc.sql("INSERT IGNORE INTO page_watch (user_id, page_id, create_date) VALUES (?, ?, ?)").params(userId, pageId, LocalDateTime.now()).update();
    }

    public void unwatchPage(int userId, int pageId) {
        jdbc.sql("DELETE FROM page_watch WHERE user_id = ? AND page_id = ?").params(userId, pageId).update();
    }

    public boolean watchesPage(int userId, int pageId) {
        return jdbc.sql("SELECT COUNT(*) FROM page_watch WHERE user_id = :user AND page_id = :page")
                .param("user", userId).param("page", pageId).query(Integer.class).single() > 0;
    }

    public void watchSpace(int userId, int spaceId) {
        jdbc.sql("INSERT IGNORE INTO space_watch (user_id, space_id, create_date) VALUES (?, ?, ?)").params(userId, spaceId, LocalDateTime.now()).update();
    }

    public void unwatchSpace(int userId, int spaceId) {
        jdbc.sql("DELETE FROM space_watch WHERE user_id = ? AND space_id = ?").params(userId, spaceId).update();
    }

    public boolean watchesSpace(int userId, int spaceId) {
        return jdbc.sql("SELECT COUNT(*) FROM space_watch WHERE user_id = :user AND space_id = :space")
                .param("user", userId).param("space", spaceId).query(Integer.class).single() > 0;
    }

    /** Active accounts watching the page itself or its space, without the actor. */
    public List<Integer> watchersOf(int pageId, int spaceId, Integer actor) {
        return jdbc.sql("""
                SELECT DISTINCT u.id FROM `user` u
                WHERE u.active = 1 AND u.deleted = 0 AND (:actor IS NULL OR u.id <> :actor)
                  AND (u.id IN (SELECT user_id FROM page_watch WHERE page_id = :page)
                       OR u.id IN (SELECT user_id FROM space_watch WHERE space_id = :space))
                ORDER BY u.id
                """).param("actor", actor).param("page", pageId).param("space", spaceId).query(Integer.class).list();
    }

    public List<WatchedPage> watchedPages(int userId) {
        return jdbc.sql("""
                SELECT p.id, p.name, p.space_id FROM page_watch w JOIN page p ON p.id = w.page_id
                WHERE w.user_id = :user AND p.deleted = 0 ORDER BY p.name
                """).param("user", userId).query((rs, row) -> new WatchedPage(rs.getInt("id"), rs.getString("name"), rs.getInt("space_id"))).list();
    }

    public List<WatchedSpace> watchedSpaces(int userId) {
        return jdbc.sql("""
                SELECT s.id, s.space_key, s.name FROM space_watch w JOIN space s ON s.id = w.space_id
                WHERE w.user_id = :user ORDER BY s.name
                """).param("user", userId).query((rs, row) -> new WatchedSpace(rs.getInt("id"), rs.getString("space_key"), rs.getString("name"))).list();
    }
}
