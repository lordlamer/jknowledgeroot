package org.knowledgeroot.app.config;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.security.auth.PasswordHasher;
import org.knowledgeroot.app.setup.InitialAdministrator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** Uses only a disposable container; never reads local application configuration. */
@Testcontainers
class InstallationTest {
    @Container
    static final MariaDBContainer<?> database = new MariaDBContainer<>("mariadb:12.2.2");
    private static final String PASSWORD = "a-test-only-password-2026";
    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void newDatabase() {
        String name = "installation_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate root = new JdbcTemplate(new DriverManagerDataSource(
                database.getJdbcUrl(), "root", database.getPassword()));
        root.execute("CREATE DATABASE " + name);
        dataSource = new DriverManagerDataSource(
                "jdbc:mariadb://" + database.getHost() + ":" + database.getMappedPort(3306) + "/" + name,
                "root", database.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void freshProductionNeedsIndividualCredentialsAndCanRestartWithoutThem() throws Exception {
        migrate(false);
        assertEquals(0, count("user"));
        assertEquals(0, count("page"));
        assertEquals(0, count("group"));
        assertThrows(IllegalStateException.class, () -> bootstrap(false, "", ""));
        assertEquals(0, count("user"));
        assertFalse(initialized());

        bootstrap(false, "FIRST.Admin", PASSWORD);
        assertEquals(1, count("user"));
        assertTrue(initialized());
        var account = jdbc.queryForMap("SELECT * FROM user");
        assertEquals("first.admin", account.get("login"));
        assertTrue(PasswordHasher.verify(PASSWORD, (String) account.get("password")));
        assertEquals(account.get("id"), account.get("created_by"));
        assertEquals(account.get("id"), account.get("changed_by"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE admin = 1 AND active = 1", Integer.class));

        migrate(false);
        bootstrap(false, "", "");
        bootstrap(false, "replacement", "another-test-password");
        assertEquals(account, jdbc.queryForMap("SELECT * FROM user"));
    }

    @Test
    void absentLiquibaseContextCannotLoadDemoData() throws Exception {
        SpringLiquibase migration = migration("classpath:dbupdates/changelog.xml", null);
        migration.afterPropertiesSet();
        assertEquals(0, count("user"));
        assertEquals(0, count("page"));
    }

    @Test
    void demoDataRequiresExplicitDevelopmentProfileAndSurvivesRestart() throws Exception {
        migrate(true);
        assertEquals(3, count("user"));
        assertEquals(3, count("page"));
        bootstrap(true, "", "");
        migrate(true);
        bootstrap(true, "", "");
        assertEquals(3, count("user"));
        assertEquals(3, count("page"));
    }

    @Test
    void enablingDemoDataOnAnExistingProductionDatabaseFailsWithoutChangingAccounts() throws Exception {
        migrate(false);
        bootstrap(false, "operator", PASSWORD);
        var users = jdbc.queryForList("SELECT * FROM user");
        assertThrows(Exception.class, () -> migrate(true));
        assertEquals(users, jdbc.queryForList("SELECT * FROM user"));
        assertEquals(0, count("page"));
    }

    @Test
    void unsafeDemoProfileCombinationsAreRejected() {
        MockEnvironment environment = new MockEnvironment().withProperty("knowledgeroot.demo-data.enabled", "true");
        assertThrows(IllegalStateException.class, () -> new LiquibaseConfig().liquibase(dataSource, environment));
        environment.setActiveProfiles("development", "production");
        assertThrows(IllegalStateException.class, () -> new LiquibaseConfig().liquibase(dataSource, environment));
    }

    @Test
    void developmentProfileAloneDoesNotOptInWhenDemoPropertyIsDisabled() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("development");
        new LiquibaseConfig().liquibase(dataSource, environment).afterPropertiesSet();
        assertEquals(0, count("user"));
    }

    @Test
    void bootstrapFailureStopsContextInitializationAfterMigrations() {
        contextRunner().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "Fresh database requires KR_BOOTSTRAP_LOGIN (3-100 ASCII letters/digits, . _ @ -) and KR_BOOTSTRAP_PASSWORD (16-128 characters). See docs/installation.md.");
            assertEquals(0, count("user"));
        });
    }

    @Test
    void springWiringMigratesBeforeCreatingTheAdministrator() {
        contextRunner().withPropertyValues("knowledgeroot.bootstrap.login=operator",
                "knowledgeroot.bootstrap.password=" + PASSWORD).run(context -> {
            assertThat(context).hasNotFailed();
            assertEquals(1, count("user"));
            assertTrue(initialized());
        });
    }

    @Test
    void invalidBootstrapValuesLeaveNoPartialAccount() throws Exception {
        migrate(false);
        assertThrows(IllegalStateException.class, () -> bootstrap(false, "operator", "short"));
        assertThrows(IllegalStateException.class, () -> bootstrap(false, " invalid ", PASSWORD));
        assertEquals(0, count("user"));
        assertFalse(initialized());
    }

    @Test
    void failureAfterInsertRollsBackAccountAndSetupMarker() throws Exception {
        migrate(false);
        jdbc.execute("""
                CREATE TRIGGER reject_bootstrap_audit BEFORE UPDATE ON user FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test audit failure'
                """);
        assertThrows(DataAccessException.class, () -> bootstrap(false, "operator", PASSWORD));
        assertEquals(0, count("user"));
        assertFalse(initialized());
        jdbc.execute("DROP TRIGGER reject_bootstrap_audit");
        bootstrap(false, "operator", PASSWORD);
        assertEquals(1, count("user"));
        assertTrue(initialized());
    }

    @Test
    void concurrentInstancesCreateOnlyOneAdministrator() throws Exception {
        migrate(false);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> bootstrap(false, "first", PASSWORD));
            var second = executor.submit(() -> bootstrap(false, "second", PASSWORD));
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        }
        assertEquals(1, count("user"));
        assertTrue(initialized());
    }

    @Test
    void removingAllAccountsDoesNotReopenBootstrap() throws Exception {
        migrate(false);
        bootstrap(false, "operator", PASSWORD);
        jdbc.update("DELETE FROM user");
        assertThrows(IllegalStateException.class, () -> bootstrap(false, "replacement", PASSWORD));
        assertEquals(0, count("user"));
    }

    @Test
    void existingAccountsWithoutAnActiveAdminAreNotReplaced() throws Exception {
        migrate(false);
        bootstrap(false, "operator", PASSWORD);
        jdbc.update("UPDATE user SET active = 0");
        assertThrows(IllegalStateException.class, () -> bootstrap(false, "replacement", PASSWORD));
        assertEquals(1, count("user"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE active = 1", Integer.class));
    }

    @Test
    void legacyUpgradePreservesChecksumsContentAccountsAndJdbcSessions() throws Exception {
        // Pre-R04 changesets and SQL, with classpath paths adjusted for the fixture.
        migration("classpath:dbupdates/legacy-changelog.xml", "production,development").afterPropertiesSet();
        var checksums = jdbc.queryForList("SELECT ID, MD5SUM FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED");
        var pages = jdbc.queryForList("SELECT * FROM page ORDER BY id");
        var users = jdbc.queryForList("SELECT * FROM user ORDER BY id");
        new ResourceDatabasePopulator(new ClassPathResource("org/springframework/session/jdbc/schema-mysql.sql"))
                .execute(dataSource);
        var repository = sessionRepository();
        Session session = saveSession(repository, "preserve this session");

        migrate(true);
        migrate(false);
        assertEquals(checksums, jdbc.queryForList(
                "SELECT ID, MD5SUM FROM DATABASECHANGELOG WHERE ID NOT IN ('1.0.5-jdbc-session', '1.0.6-initial-setup') ORDER BY ORDEREXECUTED"));
        assertEquals(pages, jdbc.queryForList("SELECT * FROM page ORDER BY id"));
        assertEquals(users, jdbc.queryForList("SELECT * FROM user ORDER BY id"));
        Session restored = sessionRepository().findById(session.getId());
        assertEquals("preserve this session", restored.getAttribute("test"));
        assertThrows(IllegalStateException.class, () -> bootstrap(false, "replacement", PASSWORD));
        assertEquals(users, jdbc.queryForList("SELECT * FROM user ORDER BY id"));

        // Simulate the documented offline rotation of all remaining demo credentials.
        jdbc.update("UPDATE user SET password = ? WHERE login IN ('admin', 'user')",
                PasswordHasher.hash(PASSWORD, PasswordHasher.HASH_METHOD.SHA256, 1000));
        bootstrap(false, "", "");
        migrate(false);
        assertEquals(pages, jdbc.queryForList("SELECT * FROM page ORDER BY id"));
        assertEquals(3, count("user"));
    }

    @Test
    void migratedSessionSchemaSupportsSaveReadAndCascadeDelete() throws Exception {
        migrate(false);
        var repository = sessionRepository();
        Session session = saveSession(repository, "hello");
        Session restored = sessionRepository().findById(session.getId());
        assertEquals("hello", restored.getAttribute("test"));
        migrate(false);
        assertNotNull(sessionRepository().findById(session.getId()));
        repository.deleteById(session.getId());
        assertEquals(0, count("SPRING_SESSION"));
        assertEquals(0, count("SPRING_SESSION_ATTRIBUTES"));
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner().withUserConfiguration(LiquibaseConfig.class, BootstrapConfig.class)
                .withBean(DataSource.class, () -> dataSource);
    }

    private JdbcIndexedSessionRepository sessionRepository() {
        return new JdbcIndexedSessionRepository(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    private <S extends Session> Session saveSession(SessionRepository<S> repository, String value) {
        S session = repository.createSession();
        session.setAttribute("test", value);
        repository.save(session);
        return session;
    }

    private void bootstrap(boolean demo, String login, String password) {
        new InitialAdministrator(dataSource, demo, login, password).initialize();
    }

    private void migrate(boolean demo) throws Exception {
        MockEnvironment environment = new MockEnvironment();
        if (demo) {
            environment.setActiveProfiles("development");
            environment.setProperty("knowledgeroot.demo-data.enabled", "true");
        }
        new LiquibaseConfig().liquibase(dataSource, environment).afterPropertiesSet();
    }

    private SpringLiquibase migration(String path, String contexts) {
        SpringLiquibase migration = new SpringLiquibase();
        migration.setDataSource(dataSource);
        migration.setChangeLog(path);
        migration.setContexts(contexts);
        return migration;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
    }

    private boolean initialized() {
        return jdbc.queryForObject("SELECT initialized FROM knowledgeroot_setup WHERE id = 1", Boolean.class);
    }
}
