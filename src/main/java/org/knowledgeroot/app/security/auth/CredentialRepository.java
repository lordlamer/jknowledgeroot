package org.knowledgeroot.app.security.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CredentialRepository {
    private final JdbcTemplate jdbc;

    public record Credential(String userId, String hash) {
        @Override public String toString() { return "Credential[redacted]"; }
    }

    public Optional<Credential> findByLogin(String login) {
        var matches = jdbc.query("SELECT id, password FROM user WHERE BINARY LOWER(login) = ? AND active = 1 AND deleted = 0",
                (rs, row) -> new Credential(rs.getString("id"), rs.getString("password")), login);
        // Ambiguous login names must never select an arbitrary account.
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public boolean replaceHash(String id, String expected, String replacement) {
        return jdbc.update("UPDATE user SET password = ? WHERE id = ? AND BINARY password = ? AND active = 1 AND deleted = 0",
                replacement, id, expected) == 1;
    }

    public boolean isCurrent(String id, String hash) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id = ? AND BINARY password = ? AND active = 1 AND deleted = 0",
                Integer.class, id, hash) == 1;
    }
}
