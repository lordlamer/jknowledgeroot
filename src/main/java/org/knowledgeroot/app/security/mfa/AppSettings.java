package org.knowledgeroot.app.security.mfa;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Application-wide settings that administrators change at runtime (table {@code app_setting}). */
@Repository
@RequiredArgsConstructor
public class AppSettings {
    private final JdbcTemplate jdbc;

    public Optional<String> get(String name) {
        return jdbc.queryForList("SELECT value FROM app_setting WHERE name=?", String.class, name).stream().findFirst();
    }

    public void set(String name, String value, Integer actor) {
        jdbc.update("""
                INSERT INTO app_setting (name, value, changed_by, change_date) VALUES (?, ?, ?, NOW(3))
                ON DUPLICATE KEY UPDATE value=VALUES(value), changed_by=VALUES(changed_by), change_date=NOW(3)
                """, name, value, actor);
    }
}
