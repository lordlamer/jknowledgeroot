package org.knowledgeroot.app.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** The notifications a user received ({@code notification}) and their mail preference ({@code notification_setting}). */
@Repository
@RequiredArgsConstructor
public class NotificationRepository {
    public static final int RETENTION_DAYS = 90;
    private static final String COLUMNS = "id, user_id, kind, page_id, page_name, actor_id, actor_login, detail, create_date, read_date, mailed";
    private final JdbcClient jdbc;

    /** One entry of the history; {@code kind} is published, comment, mention or moved. */
    public record Notification(int id, int userId, String kind, Integer pageId, String pageName, Integer actorId, String actorLogin,
                               String detail, LocalDateTime createDate, LocalDateTime readDate, boolean mailed) {
        public boolean unread() {
            return readDate == null;
        }
    }

    /** A user's mail preference; the default is immediate mails including mentions. */
    public record Preference(NotificationFrequency frequency, boolean mentions, LocalDateTime lastDigest) {
        public static final Preference DEFAULT = new Preference(NotificationFrequency.IMMEDIATE, true, null);
    }

    /** A recipient with what the mail needs. */
    public record Recipient(int id, String login, String email, String language) {}

    private static Notification notification(ResultSet rs, int row) throws SQLException {
        return new Notification(rs.getInt("id"), rs.getInt("user_id"), rs.getString("kind"), rs.getObject("page_id", Integer.class),
                rs.getString("page_name"), rs.getObject("actor_id", Integer.class), rs.getString("actor_login"), rs.getString("detail"),
                rs.getTimestamp("create_date").toLocalDateTime(),
                rs.getTimestamp("read_date") == null ? null : rs.getTimestamp("read_date").toLocalDateTime(), rs.getBoolean("mailed"));
    }

    public int create(int userId, String kind, Integer pageId, String pageName, Integer actorId, String actorLogin, String detail, boolean mailed) {
        var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.sql("INSERT INTO notification (user_id, kind, page_id, page_name, actor_id, actor_login, detail, create_date, mailed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(userId, kind, pageId, pageName, actorId, actorLogin, detail, LocalDateTime.now(), mailed).update(keys);
        return keys.getKey().intValue();
    }

    /** Newest first; {@code limit} + 1 rows tell the caller whether there is a next page. */
    public List<Notification> listForUser(int userId, int start, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM notification WHERE user_id = :user ORDER BY id DESC LIMIT :limit OFFSET :start")
                .param("user", userId).param("limit", limit + 1).param("start", start).query(NotificationRepository::notification).list();
    }

    public int countUnread(int userId) {
        return jdbc.sql("SELECT COUNT(*) FROM notification WHERE user_id = :user AND read_date IS NULL").param("user", userId).query(Integer.class).single();
    }

    public void markAllRead(int userId) {
        jdbc.sql("UPDATE notification SET read_date = ? WHERE user_id = ? AND read_date IS NULL").params(LocalDateTime.now(), userId).update();
    }

    /** Entries not yet in a mail, oldest first, for the user's next summary. */
    public List<Notification> unmailed(int userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM notification WHERE user_id = :user AND mailed = 0 ORDER BY id")
                .param("user", userId).query(NotificationRepository::notification).list();
    }

    public void markMailed(List<Integer> ids) {
        if (ids.isEmpty()) return;
        jdbc.sql("UPDATE notification SET mailed = 1 WHERE id IN (:ids)").param("ids", ids).update();
    }

    /** Users with entries waiting for a summary. */
    public List<Integer> usersWithUnmailed() {
        return jdbc.sql("SELECT DISTINCT user_id FROM notification WHERE mailed = 0 ORDER BY user_id").query(Integer.class).list();
    }

    public int deleteOlderThan(LocalDateTime before) {
        return jdbc.sql("DELETE FROM notification WHERE create_date < ?").param(before).update();
    }

    // --- preferences ------------------------------------------------------------------------------

    public Preference preference(int userId) {
        return jdbc.sql("SELECT frequency, mentions, last_digest FROM notification_setting WHERE user_id = :user").param("user", userId)
                .query((rs, row) -> new Preference(frequency(rs.getString("frequency")), rs.getBoolean("mentions"),
                        rs.getTimestamp("last_digest") == null ? null : rs.getTimestamp("last_digest").toLocalDateTime()))
                .optional().orElse(Preference.DEFAULT);
    }

    private static NotificationFrequency frequency(String stored) {
        try {
            return NotificationFrequency.fromValue(stored);
        } catch (IllegalArgumentException e) {
            return NotificationFrequency.IMMEDIATE;
        }
    }

    public void savePreference(int userId, NotificationFrequency frequency, boolean mentions) {
        jdbc.sql("""
                INSERT INTO notification_setting (user_id, frequency, mentions, change_date) VALUES (:user, :frequency, :mentions, :now)
                ON DUPLICATE KEY UPDATE frequency = VALUES(frequency), mentions = VALUES(mentions), change_date = VALUES(change_date)
                """).param("user", userId).param("frequency", frequency.value()).param("mentions", mentions).param("now", LocalDateTime.now()).update();
    }

    public void markDigest(int userId, LocalDateTime at) {
        jdbc.sql("""
                INSERT INTO notification_setting (user_id, frequency, mentions, last_digest, change_date) VALUES (:user, 'immediate', 1, :at, :at)
                ON DUPLICATE KEY UPDATE last_digest = VALUES(last_digest)
                """).param("user", userId).param("at", at).update();
    }

    // --- recipients ---------------------------------------------------------------------------------

    public Optional<Recipient> recipient(int userId) {
        return jdbc.sql("SELECT id, login, email, language FROM `user` WHERE id = :id AND active = 1 AND deleted = 0").param("id", userId)
                .query((rs, row) -> new Recipient(rs.getInt("id"), rs.getString("login"), rs.getString("email"), rs.getString("language"))).optional();
    }

    /** Active accounts whose login is among the names (case-insensitive), for mentions. */
    public List<Recipient> byLogins(List<String> logins) {
        if (logins.isEmpty()) return List.of();
        return jdbc.sql("SELECT id, login, email, language FROM `user` WHERE active = 1 AND deleted = 0 AND LOWER(login) IN (:logins)")
                .param("logins", logins.stream().map(String::toLowerCase).toList())
                .query((rs, row) -> new Recipient(rs.getInt("id"), rs.getString("login"), rs.getString("email"), rs.getString("language"))).list();
    }
}
