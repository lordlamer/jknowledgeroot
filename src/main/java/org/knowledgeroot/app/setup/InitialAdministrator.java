package org.knowledgeroot.app.setup;

import org.knowledgeroot.app.security.auth.PasswordHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Statement;
import java.util.Locale;

/** One-time, transactional setup; never resets or replaces an existing account. */
public final class InitialAdministrator {
    private static final String DEMO_HASH = "$2$1000$E556111996CF452E$B3EB80BC13DED8B02A2F09E505AED75737D4AF45D9A599623D39CD518771C306";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final boolean demoDataEnabled;
    private final String login;
    private String password;

    public InitialAdministrator(DataSource dataSource, boolean demoDataEnabled, String login, String password) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.demoDataEnabled = demoDataEnabled;
        this.login = login;
        this.password = password;
    }

    public void initialize() {
        try {
            transaction.executeWithoutResult(status -> {
                Boolean initialized = jdbc.queryForObject(
                        "SELECT initialized FROM knowledgeroot_setup WHERE id = 1 FOR UPDATE", Boolean.class);
                if (!demoDataEnabled && jdbc.queryForObject(
                        "SELECT COUNT(*) FROM user WHERE active = 1 AND deleted = 0 AND password = ?",
                        Integer.class, DEMO_HASH) > 0) {
                    throw new IllegalStateException("Active demo credentials found. Follow docs/installation.md before starting outside development.");
                }
                int userCount = jdbc.queryForObject("SELECT COUNT(*) FROM user", Integer.class);
                if (!Boolean.TRUE.equals(initialized) && userCount == 0) {
                    createAdministrator();
                }
                if (jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE admin = 1 AND active = 1 AND deleted = 0",
                        Integer.class) == 0) {
                    throw new IllegalStateException("No active administrator. Restore an existing administrator; bootstrap never replaces existing accounts. See docs/installation.md.");
                }
                jdbc.update("UPDATE knowledgeroot_setup SET initialized = TRUE WHERE id = 1");
            });
        } finally {
            password = null;
        }
    }

    private void createAdministrator() {
        if (login == null || !login.matches("[A-Za-z0-9][A-Za-z0-9._@-]{2,99}")
                || password == null || password.length() < 16 || password.length() > 128 || password.isBlank()) {
            throw new IllegalStateException("Fresh database requires KR_BOOTSTRAP_LOGIN (3-100 ASCII letters/digits, . _ @ -) and KR_BOOTSTRAP_PASSWORD (16-128 characters). See docs/installation.md.");
        }
        // Keep compatibility with the existing login provider; encoder migration is roadmap R06.
        String hash = PasswordHasher.hash(password, PasswordHasher.HASH_METHOD.SHA256, 1000);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO user (login, password, admin, active, language, timezone,
                                      created_by, create_date, changed_by, change_date)
                    VALUES (?, ?, TRUE, TRUE, 'en_US', 'UTC', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, login.toLowerCase(Locale.ROOT));
            statement.setString(2, hash);
            return statement;
        }, key);
        Number id = key.getKey();
        if (id == null) {
            throw new IllegalStateException("Administrator creation did not return an ID");
        }
        jdbc.update("UPDATE user SET created_by = ?, changed_by = ? WHERE id = ?", id, id, id);
    }
}
