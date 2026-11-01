package org.knowledgeroot.app.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Audit of the MCP server (table {@code mcp_audit}): who called which tool, resource or prompt,
 * with what parameters (summarised, never page contents), with which outcome and how long it
 * took. Administrators read it under Admin → MCP; entries older than the retention are swept.
 */
@Repository
@RequiredArgsConstructor
public class McpAudit {
    public static final int RETENTION_DAYS = 90;

    public record Entry(long id, LocalDateTime createdAt, Integer userId, String login, String clientId, String clientName,
                        String kind, String name, String parameters, String outcome, String detail, int durationMs,
                        String remoteAddr) {}

    private final JdbcTemplate jdbc;

    public void record(Integer userId, String login, String clientId, String kind, String name, String parameters,
                       String outcome, String detail, long durationMs, String remoteAddr) {
        jdbc.update("""
                INSERT INTO mcp_audit (created_at, user_id, login, client_id, kind, name, parameters, outcome, detail, duration_ms, remote_addr)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, LocalDateTime.now(), userId, cut(login, 255), cut(clientId, 100), cut(kind, 16), cut(name, 64),
                cut(parameters, 1000), cut(outcome, 16), cut(detail, 255), (int) Math.min(durationMs, Integer.MAX_VALUE), cut(remoteAddr, 64));
    }

    /** Newest first; {@code login} narrows to one account (exact match), {@code limit + 1} rows signal a next page. */
    public List<Entry> list(String login, int start, int limit) {
        String filter = login == null || login.isBlank() ? "" : " WHERE a.login = ?";
        Object[] args = login == null || login.isBlank() ? new Object[]{limit, start} : new Object[]{login.trim(), limit, start};
        return jdbc.query("""
                SELECT a.id, a.created_at, a.user_id, a.login, a.client_id, c.client_name, a.kind, a.name, a.parameters,
                       a.outcome, a.detail, a.duration_ms, a.remote_addr
                FROM mcp_audit a LEFT JOIN oauth2_registered_client c ON c.id = a.client_id
                """ + filter + " ORDER BY a.id DESC LIMIT ? OFFSET ?", (rs, row) -> new Entry(rs.getLong("id"),
                time(rs.getTimestamp("created_at")), rs.getObject("user_id", Integer.class), rs.getString("login"),
                rs.getString("client_id"), rs.getString("client_name"), rs.getString("kind"), rs.getString("name"),
                rs.getString("parameters"), rs.getString("outcome"), rs.getString("detail"), rs.getInt("duration_ms"),
                rs.getString("remote_addr")), args);
    }

    public int sweep(LocalDateTime before) {
        return jdbc.update("DELETE FROM mcp_audit WHERE created_at < ?", before);
    }

    private static String cut(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
