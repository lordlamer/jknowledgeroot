package org.knowledgeroot.app.security.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/** Reserve attempts before hashing, atomically across application instances. */
@Component
public class LoginAttemptLimiter {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    @Value("${knowledgeroot.login.account-attempts:5}") private int accountAttempts = 5;
    @Value("${knowledgeroot.login.source-attempts:30}") private int sourceAttempts = 30;
    @Value("${knowledgeroot.login.window-seconds:60}") private int windowSeconds = 60;

    public LoginAttemptLimiter(DataSource source) {
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @PostConstruct
    void validateConfiguration() {
        if (accountAttempts < 1 || accountAttempts > 1_000_000 || sourceAttempts < 1 || sourceAttempts > 1_000_000
                || windowSeconds < 1 || windowSeconds > 3600) {
            throw new IllegalStateException("Login attempt limits must be 1-1000000; window must be 1-3600 seconds");
        }
    }

    public void requireAttempt(String normalizedLogin, String remoteAddress) {
        requireAttempts("account:" + normalizedLogin, "source:" + remoteAddress);
    }

    public void requirePasswordChangeAttempt(String userId, String remoteAddress) {
        requireAttempts("password-change-account:" + userId, "password-change-source:" + remoteAddress);
    }

    private void requireAttempts(String accountKey, String sourceKey) {
        removeExpiredBuckets();
        boolean allowed = Boolean.TRUE.equals(transaction.execute(status -> {
            long now = jdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED)", Long.class);
            // Names and addresses are stored only as namespaced hashes.
            boolean account = reserve(accountKey, accountAttempts, now);
            boolean source = reserve(sourceKey, sourceAttempts, now);
            return account && source;
        }));
        // Throw after commit so rejected attempts cannot roll back the counters.
        if (!allowed) throw new BadCredentialsException("Invalid username or password");
    }

    private void removeExpiredBuckets() {
        // Select without locks, then delete individual expired rows. A range DELETE can
        // deadlock with concurrent reservations even when there are no expired rows.
        var keys = jdbc.queryForList("""
                SELECT bucket_key FROM login_attempt
                WHERE window_start < CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED) - 3600000
                LIMIT 100
                """, String.class);
        for (String key : keys) {
            try {
                jdbc.update("""
                        DELETE FROM login_attempt WHERE bucket_key = ?
                        AND window_start < CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED) - 3600000
                        """, key);
            } catch (ConcurrencyFailureException ignored) {
                // Cleanup is best effort; a later request retries. Reservation failures
                // still propagate and prevent authentication.
                return;
            }
        }
    }

    private boolean reserve(String identifier, int limit, long now) {
        String key = PasswordService.credentialTag(identifier);
        long cutoff = now - windowSeconds * 1000L;
        jdbc.update("""
                INSERT INTO login_attempt (bucket_key, window_start, attempts) VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE
                    attempts = IF(window_start <= ?, 1, LEAST(attempts + 1, ?)),
                    window_start = IF(window_start <= ?, ?, window_start)
                """, key, now, cutoff, (long) limit + 1, cutoff, now);
        return jdbc.queryForObject("SELECT attempts FROM login_attempt WHERE bucket_key = ?", Integer.class, key) <= limit;
    }
}
