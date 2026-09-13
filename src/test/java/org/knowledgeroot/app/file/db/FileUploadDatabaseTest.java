package org.knowledgeroot.app.file.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.knowledgeroot.app.config.LiquibaseConfig;
import org.knowledgeroot.app.file.domain.FileUploadService;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class FileUploadDatabaseTest {
    @Container static final MariaDBContainer<?> database = new MariaDBContainer<>(org.knowledgeroot.test.DatabaseImage.MARIADB);
    @TempDir Path directory;
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private FileSystemStorage storage;
    private final org.knowledgeroot.app.file.domain.UploadPolicy policy = new org.knowledgeroot.app.file.domain.UploadPolicy("25MB", "100MB", 10);
    private UserContext users;
    private PagePermissionDao permissions;

    @BeforeEach
    void setUp() throws Exception {
        String name = "uploads_" + UUID.randomUUID().toString().replace("-", "");
        var root = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), "root", database.getPassword()));
        root.execute("CREATE DATABASE " + name);
        source = new DriverManagerDataSource("jdbc:mariadb://" + database.getHost() + ":"
                + database.getMappedPort(3306) + "/" + name, "root", database.getPassword());
        new LiquibaseConfig().liquibase(source, new MockEnvironment()).afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
        jdbc.update("""
                INSERT INTO user (id, login, active, created_by, create_date, changed_by, change_date)
                VALUES (10, 'uploader', TRUE, 10, NOW(), 10, NOW())
                """);
        jdbc.update("""
                INSERT INTO page (id, name, active, created_by, create_date, changed_by, change_date)
                VALUES (100, 'page', TRUE, 10, NOW(), 10, NOW())
                """);
        storage = spy(new FileSystemStorage(directory.toString()));
        users = mock(UserContext.class);
        when(users.getUserContext()).thenReturn(UserDetails.builder().userId("10").role(UserDetails.Role.USER).build());
        permissions = mock(PagePermissionDao.class);
        when(permissions.hasUserPermission(any(), any(), any())).thenReturn(true);
    }

    @Test
    void successfulBatchCommitsMetadataWithActualUserAndGuestAudit() {
        context().run(context -> {
            var uploads = context.getBean(FileUploadService.class);
            uploads.upload(100, file("one", "one"), file("two", "two"));
            assertEquals(2, count());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM file WHERE created_by = 10 AND changed_by = 10", Integer.class));
            when(users.getUserContext()).thenReturn(UserDetails.builder().role(UserDetails.Role.GUEST).build());
            uploads.upload(100, file("guest", "guest"));
            int id = jdbc.queryForObject("SELECT id FROM file WHERE name = 'guest'", Integer.class);
            var meta = new FileImpl(jdbc, storage, policy).findById(id);
            assertNull(meta.getCreatedBy());
            assertNull(meta.getChangedBy());
            try (var input = storage.retrieve(meta.getHash())) {
                assertArrayEquals("guest".getBytes(java.nio.charset.StandardCharsets.UTF_8), input.readAllBytes());
            }
        });
    }

    @Test
    void laterStorageFailureRollsBackEntireBatchAndKeepsExistingObjects() {
        context().run(context -> {
            var uploads = context.getBean(FileUploadService.class);
            uploads.upload(100, file("existing", "shared"));
            String existingHash = jdbc.queryForObject("SELECT hash FROM file", String.class);
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("failure after storing object");
            }).when(storage).store(anyString(), any());
            assertThrows(IllegalStateException.class, () -> uploads.upload(100,
                    file("reused", "shared"), file("failed", "new object")));
            assertEquals(1, count());
            assertTrue(storage.exists(existingHash));
            verify(storage, never()).delete(anyString());
        });
    }

    @Test
    void databaseFailureAfterStorageRollsBackAllMetadata() {
        jdbc.execute("ALTER TABLE file ADD CONSTRAINT reject_test_upload CHECK (name <> 'reject')");
        context().run(context -> {
            var uploads = context.getBean(FileUploadService.class);
            assertThrows(DataIntegrityViolationException.class, () -> uploads.upload(100,
                    file("valid", "first"), file("reject", "second")));
            assertEquals(0, count());
            try (var objects = Files.list(directory)) {
                assertEquals(2, objects.count()); // Complete, unreferenced objects are retained deliberately.
            }
        });
    }

    @Test
    void invalidBatchIsRejectedBeforeAnyStorageWrite() {
        context().run(context -> {
            assertThrows(ResponseStatusException.class, () -> context.getBean(FileUploadService.class)
                    .upload(100, file("valid", "first"), file("empty", "")));
            assertEquals(0, count());
            verify(storage, never()).store(anyString(), any());
        });
    }

    @Test
    void deniedUploadDoesNotWriteStorageOrMetadata() {
        when(permissions.hasUserPermission(any(), any(), any())).thenReturn(false);
        context().run(context -> {
            assertThrows(ResponseStatusException.class, () -> context.getBean(FileUploadService.class)
                    .upload(100, file("valid", "first")));
            assertEquals(0, count());
            verify(storage, never()).store(anyString(), any());
        });
    }

    @Test
    void interruptedLocalWriteLeavesNoVisiblePartialObjectAndRetryWorks() throws Exception {
        InputStream broken = new InputStream() {
            private int remaining = 10;
            @Override public int read() throws IOException {
                if (--remaining < 0) throw new IOException("interrupted input");
                return 'x';
            }
        };
        assertThrows(RuntimeException.class, () -> storage.store("0123456789abcdef0123456789abcdef", broken));
        assertFalse(storage.exists("0123456789abcdef0123456789abcdef"));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        storage.store("0123456789abcdef0123456789abcdef", new ByteArrayInputStream(new byte[]{1, 2, 3}));
        try (var input = storage.retrieve("0123456789abcdef0123456789abcdef")) { assertArrayEquals(new byte[]{1, 2, 3}, input.readAllBytes()); }
    }

    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM file", Integer.class); }

    @Test
    void unicodeFilenameAndMissingContentTypeAreStoredSafely() {
        context().run(context -> {
            context.getBean(FileUploadService.class).upload(100,
                    new MockMultipartFile("file", "C:\\fakepath\\Büro 📎.txt", null, new byte[]{4, 5}));
            var file = new FileImpl(jdbc, storage, policy).listFiles(null).getFirst();
            assertEquals("Büro 📎.txt", file.getName());
            assertEquals("application/octet-stream", file.getType());
        });
    }

    @Test
    void legacyMd5ObjectRemainsReadableWhileIdenticalNewUploadUsesSha256() {
        String legacy = "0123456789abcdef0123456789abcdef";
        storage.store(legacy, new ByteArrayInputStream(new byte[]{1, 2, 3}));
        jdbc.update("INSERT INTO file (page_id, hash, name, size, create_date, change_date) VALUES (100, ?, 'legacy', 3, NOW(), NOW())", legacy);
        context().run(context -> {
            context.getBean(FileUploadService.class).upload(100, new MockMultipartFile("file", "new", "text/plain", new byte[]{1, 2, 3}) {
                @Override public byte[] getBytes() { throw new AssertionError("upload must stream"); }
            });
            var dao = new FileImpl(jdbc, storage, policy);
            for (int id : jdbc.queryForList("SELECT id FROM file", Integer.class)) {
                try (var input = dao.loadFile(id)) { assertArrayEquals(new byte[]{1, 2, 3}, input.readAllBytes()); }
            }
            assertEquals(legacy, jdbc.queryForObject("SELECT hash FROM file WHERE name = 'legacy'", String.class));
            assertEquals("sha256-039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81", jdbc.queryForObject("SELECT hash FROM file WHERE name = 'new'", String.class));
        });
    }

    @Test
    void concurrentUploadsKeepAllMetadataAndShareOneCompleteObject() {
        context().run(context -> {
            var service = context.getBean(FileUploadService.class);
            try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
                var tasks = java.util.stream.IntStream.range(0, 8).<java.util.concurrent.Callable<Void>>mapToObj(i -> () -> {
                    service.upload(100, file("same-" + i, "shared content"));
                    return null;
                }).toList();
                for (var future : executor.invokeAll(tasks)) future.get();
            }
            assertEquals(8, count());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(DISTINCT hash) FROM file", Integer.class));
            try (var objects = Files.list(directory)) { assertEquals(1, objects.count()); }
        });
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {}

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(Transactions.class)
                .withBean("transactionManager", DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(source))
                .withBean(FileUploadService.class, () -> new FileUploadService(new FileImpl(jdbc, storage, policy), permissions, users, policy));
    }
}
