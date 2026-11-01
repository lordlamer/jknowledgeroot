package org.knowledgeroot.app.mcp.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The assistants a user connected, read from the authorization table: one entry per registered
 * client with live tokens. Disconnecting deletes the client's authorizations and consent for the
 * user, so its refresh token stops working at once and its access token at the latest after
 * fifteen minutes. A scheduled sweep removes expired authorizations and abandoned registrations.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class McpConnections {
    private final JdbcTemplate jdbc;
    private final org.knowledgeroot.app.mcp.McpAudit audit;

    /** A connection as administrators see it: with the account it belongs to. */
    public record AdminConnection(int userId, String login, Connection connection) {}

    public record Connection(String clientId, String clientName, String redirectHost, LocalDateTime connectedAt,
                             LocalDateTime lastRefreshedAt, LocalDateTime validUntil, Set<String> scopes) {
        public boolean canWrite() {
            return scopes.contains(org.knowledgeroot.app.mcp.McpUserToken.SCOPE_WRITE);
        }
    }

    public List<Connection> list(int userId) {
        return jdbc.query("""
                SELECT c.id, c.client_name, c.redirect_uris, MIN(a.authorization_code_issued_at) AS connected_at,
                       MAX(a.refresh_token_issued_at) AS refreshed_at, MAX(a.refresh_token_expires_at) AS valid_until,
                       GROUP_CONCAT(DISTINCT a.authorized_scopes SEPARATOR ',') AS scopes
                FROM oauth2_authorization a JOIN oauth2_registered_client c ON c.id = a.registered_client_id
                WHERE a.principal_name = ? AND (a.refresh_token_expires_at > ? OR a.access_token_expires_at > ?)
                GROUP BY c.id, c.client_name, c.redirect_uris
                ORDER BY refreshed_at DESC, connected_at DESC
                """, (rs, row) -> new Connection(rs.getString("id"), rs.getString("client_name"),
                        host(rs.getString("redirect_uris")), time(rs.getTimestamp("connected_at")),
                        time(rs.getTimestamp("refreshed_at")), time(rs.getTimestamp("valid_until")), scopes(rs.getString("scopes"))),
                String.valueOf(userId), LocalDateTime.now(), LocalDateTime.now());
    }

    /** Every user's live connections, newest activity first. */
    public List<AdminConnection> listAll() {
        return jdbc.query("""
                SELECT a.principal_name, u.login, c.id, c.client_name, c.redirect_uris, MIN(a.authorization_code_issued_at) AS connected_at,
                       MAX(a.refresh_token_issued_at) AS refreshed_at, MAX(a.refresh_token_expires_at) AS valid_until,
                       GROUP_CONCAT(DISTINCT a.authorized_scopes SEPARATOR ',') AS scopes
                FROM oauth2_authorization a JOIN oauth2_registered_client c ON c.id = a.registered_client_id
                LEFT JOIN user u ON CAST(u.id AS CHAR) = a.principal_name
                WHERE a.refresh_token_expires_at > ? OR a.access_token_expires_at > ?
                GROUP BY a.principal_name, u.login, c.id, c.client_name, c.redirect_uris
                ORDER BY refreshed_at DESC, connected_at DESC
                """, (rs, row) -> new AdminConnection(Integer.parseInt(rs.getString("principal_name")), rs.getString("login"),
                        new Connection(rs.getString("id"), rs.getString("client_name"), host(rs.getString("redirect_uris")),
                                time(rs.getTimestamp("connected_at")), time(rs.getTimestamp("refreshed_at")),
                                time(rs.getTimestamp("valid_until")), scopes(rs.getString("scopes")))),
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Transactional
    public boolean revoke(int userId, String clientId) {
        String principal = String.valueOf(userId);
        int removed = jdbc.update("DELETE FROM oauth2_authorization WHERE principal_name = ? AND registered_client_id = ?", principal, clientId);
        jdbc.update("DELETE FROM oauth2_authorization_consent WHERE principal_name = ? AND registered_client_id = ?", principal, clientId);
        if (removed > 0) log.info("User {} disconnected MCP client {}", userId, clientId);
        return removed > 0;
    }

    /** Authorizations whose tokens all expired a day ago, and registrations nobody used within a week. */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    @Transactional
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        int authorizations = jdbc.update("""
                DELETE FROM oauth2_authorization
                WHERE COALESCE(refresh_token_expires_at, access_token_expires_at, authorization_code_expires_at) < ?
                """, now.minusDays(1));
        int clients = jdbc.update("""
                DELETE FROM oauth2_registered_client
                WHERE client_id_issued_at < ?
                  AND NOT EXISTS (SELECT 1 FROM oauth2_authorization a WHERE a.registered_client_id = oauth2_registered_client.id)
                  AND NOT EXISTS (SELECT 1 FROM oauth2_authorization_consent s WHERE s.registered_client_id = oauth2_registered_client.id)
                """, now.minusDays(7));
        int entries = audit.sweep(now.minusDays(org.knowledgeroot.app.mcp.McpAudit.RETENTION_DAYS));
        if (authorizations > 0 || clients > 0 || entries > 0)
            log.info("MCP sweep removed {} authorizations, {} clients and {} audit entries", authorizations, clients, entries);
    }

    private static LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Set<String> scopes(String joined) {
        var scopes = new LinkedHashSet<String>();
        if (joined != null) Arrays.stream(joined.split("[,\\s]+")).filter(s -> !s.isBlank()).forEach(scopes::add);
        return scopes;
    }

    private static String host(String redirectUris) {
        if (redirectUris == null || redirectUris.isBlank()) return "";
        try {
            String host = URI.create(redirectUris.split(",")[0].trim()).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
