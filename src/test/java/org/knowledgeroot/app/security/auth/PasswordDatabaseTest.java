package org.knowledgeroot.app.security.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.config.LiquibaseConfig;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class PasswordDatabaseTest {
    @Container static final MariaDBContainer<?> database = new MariaDBContainer<>("mariadb:12.2.2");
    private static final String LEGACY = PasswordHasher.hash("old", PasswordHasher.HASH_METHOD.SHA256, 1000);
    private static final PasswordService passwords = new PasswordService();
    private DataSource source;
    private JdbcTemplate jdbc;
    private CredentialRepository credentials;

    @BeforeEach
    void setUp() throws Exception {
        String name = "passwords_" + UUID.randomUUID().toString().replace("-", "");
        var root = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), "root", database.getPassword()));
        root.execute("CREATE DATABASE " + name);
        source = new DriverManagerDataSource("jdbc:mariadb://" + database.getHost() + ":"
                + database.getMappedPort(3306) + "/" + name, "root", database.getPassword());
        new LiquibaseConfig().liquibase(source, new MockEnvironment()).afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
        credentials = spy(new CredentialRepository(jdbc));
        jdbc.update("""
                INSERT INTO user (id, login, password, active, admin, created_by, create_date, changed_by, change_date)
                VALUES (1, 'alice', ?, TRUE, TRUE, 1, NOW(), 1, NOW())
                """, LEGACY);
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("org.knowledgeroot.app.security.context")
    static class Accounts {}

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(Accounts.class)
                .withBean(JdbcClient.class, () -> JdbcClient.create(source))
                .withBean(CredentialRepository.class, () -> credentials)
                .withBean(PasswordService.class, () -> passwords)
                .withBean(LoginAttemptLimiter.class, () -> new LoginAttemptLimiter(source))
                .withBean(DatabaseAuthentificationProvider.class);
    }

    @Test
    void successfulLegacyLoginMigratesAndReloadsTheCurrentAccountById() {
        context().run(context -> {
            var provider = context.getBean(DatabaseAuthentificationProvider.class);
            var authentication = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("ALICE", "old"));
            assertTrue(authentication.isAuthenticated());
            String migrated = storedHash();
            assertTrue(migrated.startsWith(PasswordService.PREFIX));
            assertTrue(passwords.matches("old", migrated));
            assertEquals(PasswordService.credentialTag(migrated), ((UserDetails) authentication.getDetails()).getCredentialTag());
            provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("alice", "old"));
            assertEquals(migrated, storedHash());
            verify(credentials, times(1)).replaceHash(eq("1"), eq(LEGACY), anyString());
            jdbc.update("UPDATE user SET password = ? WHERE id = 1", passwords.encodeNewPassword("a-new-password-for-alice"));
            assertNotEquals(((UserDetails) authentication.getDetails()).getCredentialTag(),
                    context.getBean(UserContext.class).getUserContextForId("1").getCredentialTag());
        });
    }

    @Test
    void failedLoginDoesNotMigrateAndUnknownAccountsFailTheSameWay() {
        context().run(context -> {
            var provider = context.getBean(DatabaseAuthentificationProvider.class);
            var wrong = assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("alice", "wrong")));
            var missing = assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("unknown", "wrong")));
            assertEquals(wrong.getMessage(), missing.getMessage());
            assertEquals(LEGACY, storedHash());
            verify(credentials, never()).replaceHash(anyString(), anyString(), anyString());
        });
    }

    @Test
    void concurrentPasswordResetCannotBeOverwrittenByMigration() {
        String reset = passwords.encodeNewPassword("concurrently-reset-password");
        doAnswer(call -> {
            jdbc.update("UPDATE user SET password = ? WHERE id = 1", reset);
            return call.callRealMethod();
        }).when(credentials).replaceHash(eq("1"), eq(LEGACY), anyString());
        context().run(context -> {
            var provider = context.getBean(DatabaseAuthentificationProvider.class);
            assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("alice", "old")));
            assertEquals(reset, storedHash());
            assertTrue(provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    "alice", "concurrently-reset-password")).isAuthenticated());
        });
    }

    @Test
    void ambiguousLoginNamesDoNotSelectAnArbitraryAccount() {
        jdbc.update("""
                INSERT INTO user (login, password, active, created_by, create_date, changed_by, change_date)
                VALUES ('ALICE', ?, TRUE, 1, NOW(), 1, NOW())
                """, LEGACY);
        assertTrue(credentials.findByLogin("alice").isEmpty());
    }

    @Test
    void accountQuotaSurvivesNewLimiterInstanceAndResetsAfterWindow() {
        var limiter = new LoginAttemptLimiter(source);
        for (int attempt = 0; attempt < 5; attempt++) limiter.requireAttempt("alice", "127.0.0.1");
        assertThrows(BadCredentialsException.class, () -> new LoginAttemptLimiter(source).requireAttempt("alice", "127.0.0.2"));
        jdbc.update("UPDATE login_attempt SET window_start = 0");
        new LoginAttemptLimiter(source).requireAttempt("alice", "127.0.0.1");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt", Integer.class));
        assertTrue(jdbc.queryForList("SELECT bucket_key FROM login_attempt", String.class).stream()
                .allMatch(key -> key.matches("[0-9a-f]{64}")));
    }

    @Test
    void sourceQuotaAppliesAcrossDifferentAccountNames() {
        var limiter = new LoginAttemptLimiter(source);
        ReflectionTestUtils.setField(limiter, "sourceAttempts", 2);
        limiter.requireAttempt("alice", "127.0.0.1");
        limiter.requireAttempt("unknown", "127.0.0.1");
        assertThrows(BadCredentialsException.class, () -> limiter.requireAttempt("third", "127.0.0.1"));
        limiter.requireAttempt("third", "127.0.0.2");
    }

    @Test
    void parallelInstancesCannotOverrunTheAccountQuota() throws Exception {
        var first = new LoginAttemptLimiter(source);
        var second = new LoginAttemptLimiter(source);
        // Exercise concurrent cleanup of expired rows as well as creation of fresh buckets.
        jdbc.update("INSERT INTO login_attempt (bucket_key, window_start, attempts) VALUES (?, 0, 6)",
                PasswordService.credentialTag("account:alice0"));
        for (int address = 0; address < 16; address++) {
            jdbc.update("INSERT INTO login_attempt (bucket_key, window_start, attempts) VALUES (?, 0, 31)",
                    PasswordService.credentialTag("source:127.0.0." + address));
        }
        for (int round = 0; round < 5; round++) {
            assertParallelQuota(first, second, "alice" + round);
        }
    }

    private void assertParallelQuota(LoginAttemptLimiter first, LoginAttemptLimiter second, String login) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        var results = new ArrayList<Future<Boolean>>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int attempt = 0; attempt < 16; attempt++) {
                final int index = attempt;
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        (index % 2 == 0 ? first : second).requireAttempt(login, "127.0.0." + index);
                        return true;
                    } catch (BadCredentialsException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (var result : results) if (result.get(30, TimeUnit.SECONDS)) accepted++;
            assertEquals(5, accepted);
        }
    }

    private String storedHash() {
        return jdbc.queryForObject("SELECT password FROM user WHERE id = 1", String.class);
    }
}
