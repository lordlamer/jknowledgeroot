package org.knowledgeroot.app.page.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.config.LiquibaseConfig;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PageCreationService;
import org.knowledgeroot.app.page.domain.PageEditingService;
import org.knowledgeroot.app.page.domain.PageLabelDao;
import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.knowledgeroot.app.page.domain.PagePermission.PermissionLevel.*;
import static org.mockito.Mockito.*;

/** Regression coverage for existing grants, independent of new-page defaults. */
@Testcontainers
class PagePermissionDatabaseTest {
    @Container
    static final MariaDBContainer<?> database = new MariaDBContainer<>("mariadb:12.2.2");
    private JdbcTemplate jdbc;
    private PagePermissionImpl permissions;
    private DataSource dataSource;
    private final PageId page = new PageId(100);
    private final PageId otherPage = new PageId(101);

    @BeforeEach
    void setUp() throws Exception {
        String name = "permissions_" + UUID.randomUUID().toString().replace("-", "");
        var root = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), "root", database.getPassword()));
        root.execute("CREATE DATABASE " + name);
        var source = new DriverManagerDataSource(
                "jdbc:mariadb://" + database.getHost() + ":" + database.getMappedPort(3306) + "/" + name,
                "root", database.getPassword());
        new LiquibaseConfig().liquibase(source, new MockEnvironment()).afterPropertiesSet();
        dataSource = source;
        jdbc = new JdbcTemplate(source);
        permissions = new PagePermissionImpl(JdbcClient.create(source));
        jdbc.update("""
                INSERT INTO user (id, login, admin, active, created_by, create_date, changed_by, change_date)
                VALUES (10, 'reader', FALSE, TRUE, 10, NOW(), 10, NOW()),
                       (11, 'other', FALSE, TRUE, 11, NOW(), 11, NOW()),
                       (12, 'admin', TRUE, TRUE, 12, NOW(), 12, NOW())
                """);
        jdbc.update("""
                INSERT INTO page (id, name, active, created_by, create_date, changed_by, change_date)
                VALUES (100, 'protected', TRUE, 12, NOW(), 12, NOW()),
                       (101, 'other', TRUE, 12, NOW(), 12, NOW())
                """);
        jdbc.update("""
                INSERT INTO `group` (id, name, active, created_by, create_date, changed_by, change_date)
                VALUES (20, 'team', TRUE, 12, NOW(), 12, NOW())
                """);
    }

    @Test
    void directGrantDoesNotGiveRightsToAnotherUserOrPage() {
        grant(PagePermission.RoleType.USER, 10, VIEW);
        assertTrue(permissions.hasUserPermission(page, 10, VIEW));
        assertFalse(permissions.hasUserPermission(page, 10, EDIT));
        assertFalse(permissions.hasUserPermission(page, 11, VIEW));
        assertFalse(permissions.hasUserPermission(otherPage, 10, VIEW));
        assertFalse(permissions.hasUserPermission(page, null, VIEW));
    }

    @Test
    void groupEditGrantRequiresCurrentMembership() {
        grant(PagePermission.RoleType.GROUP, 20, EDIT);
        assertFalse(permissions.hasUserPermission(page, 10, VIEW));
        jdbc.update("INSERT INTO group_member (group_id, member_id, member_type) VALUES (20, 10, 'user')");
        assertTrue(permissions.hasUserPermission(page, 10, VIEW));
        assertTrue(permissions.hasUserPermission(page, 10, EDIT));
        assertFalse(permissions.hasUserPermission(page, 11, VIEW));
        jdbc.update("DELETE FROM group_member WHERE group_id = 20 AND member_id = 10");
        assertFalse(permissions.hasUserPermission(page, 10, VIEW));
    }

    @Test
    void explicitPublicReadGrantRemainsReadableWithoutGrantingEdit() {
        grant(PagePermission.RoleType.GUEST, null, VIEW);
        assertTrue(permissions.hasUserPermission(page, null, VIEW));
        assertTrue(permissions.hasUserPermission(page, 10, VIEW));
        assertFalse(permissions.hasUserPermission(page, null, EDIT));
        assertFalse(permissions.hasUserPermission(otherPage, null, VIEW));
    }

    @Test
    void administratorCanReadAndEditWithoutExplicitGrant() {
        assertTrue(permissions.hasUserPermission(page, 12, VIEW));
        assertTrue(permissions.hasUserPermission(page, 12, EDIT));
        assertFalse(permissions.hasUserPermission(page, 10, VIEW));
    }

    @Test
    void updateCannotChangePermissionBelongingToAnotherPage() {
        grant(PagePermission.RoleType.USER, 10, VIEW);
        PagePermission existing = permissions.listPermissionsByPageId(page).getFirst();
        PagePermission update = PagePermission.builder().id(existing.getId()).permissionLevel(EDIT)
                .changedBy(12).changeDate(LocalDateTime.now()).build();
        assertThrows(IllegalStateException.class, () -> permissions.updatePermissionForPage(otherPage, update));
        assertEquals(VIEW, permissions.listPermissionsByPageId(page).getFirst().getPermissionLevel());
        permissions.updatePermissionForPage(page, update);
        assertTrue(permissions.hasUserPermission(page, 10, EDIT));
    }

    @Test
    void deleteCannotRemovePermissionBelongingToAnotherPage() {
        grant(PagePermission.RoleType.USER, 10, VIEW);
        int id = permissions.listPermissionsByPageId(page).getFirst().getId();
        assertThrows(IllegalStateException.class, () -> permissions.deletePermissionForPage(otherPage, id));
        assertTrue(permissions.hasUserPermission(page, 10, VIEW));
        permissions.deletePermissionForPage(page, id);
        assertFalse(permissions.hasUserPermission(page, 10, VIEW));
    }

    @Test
    void newChildAndGrandchildTrackParentRevocationWithoutCreatorBypass() {
        grant(PagePermission.RoleType.USER, 10, EDIT);
        jdbc.update("UPDATE page SET parent = 100 WHERE id = 101");
        permissions.createDefaultPermissions(otherPage, 10);
        jdbc.update("""
                INSERT INTO page (id, parent, name, create_date, change_date)
                VALUES (102, 101, 'grandchild', NOW(), NOW())
                """);
        PageId grandchild = new PageId(102);
        permissions.createDefaultPermissions(grandchild, 10);
        assertTrue(permissions.isInheriting(otherPage));
        assertTrue(permissions.hasUserPermission(grandchild, 10, EDIT));
        assertTrue(permissions.listPermissionsByPageId(otherPage).isEmpty());
        assertFalse(permissions.hasUserPermission(grandchild, null, VIEW));
        jdbc.update("DELETE FROM page_permission WHERE page_id = 100");
        assertFalse(permissions.hasUserPermission(otherPage, 10, VIEW));
        assertFalse(permissions.hasUserPermission(grandchild, 10, VIEW));
    }

    @Test
    void localOverrideIsExplicitAndReenablingInheritanceRemovesIt() {
        grant(PagePermission.RoleType.USER, 10, VIEW);
        jdbc.update("UPDATE page SET parent = 100 WHERE id = 101");
        permissions.createDefaultPermissions(otherPage, 10);
        permissions.setInheriting(otherPage, false, 12);
        assertFalse(permissions.isInheriting(otherPage));
        assertTrue(permissions.hasUserPermission(otherPage, 10, VIEW));
        jdbc.update("DELETE FROM page_permission WHERE page_id = 100");
        assertTrue(permissions.hasUserPermission(otherPage, 10, VIEW));
        assertFalse(permissions.hasUserPermission(page, 10, VIEW));
        permissions.setInheriting(otherPage, true, 12);
        assertFalse(permissions.hasUserPermission(otherPage, 10, VIEW));
        assertTrue(permissions.listPermissionsByPageId(otherPage).isEmpty());
    }

    @Test
    void inheritedPermissionsCannotBeChangedThroughLocalGrantEndpoints() {
        grant(PagePermission.RoleType.USER, 10, VIEW);
        jdbc.update("UPDATE page SET parent = 100 WHERE id = 101");
        permissions.createDefaultPermissions(otherPage, 10);
        PagePermission permission = permissions.listPermissionsByPageId(page).getFirst();
        permission.setPageId(otherPage);
        assertThrows(IllegalStateException.class, () -> permissions.createPermission(permission));
        assertThrows(IllegalStateException.class, () -> permissions.updatePermissionForPage(otherPage, permission));
        assertThrows(IllegalStateException.class, () -> permissions.deletePermissionForPage(otherPage, permission.getId()));
        assertTrue(permissions.hasUserPermission(otherPage, 10, VIEW));
    }

    @Test
    void brokenAndCyclicInheritanceFailsClosed() {
        grant(PagePermission.RoleType.GUEST, null, VIEW);
        jdbc.update("UPDATE page SET parent = 101, inherit_permissions = TRUE WHERE id = 100");
        jdbc.update("UPDATE page SET parent = 100, inherit_permissions = TRUE WHERE id = 101");
        assertNull(permissions.permissionSource(page));
        assertFalse(permissions.hasUserPermission(page, null, VIEW));
        assertFalse(permissions.hasUserPermission(page, 10, VIEW));
        jdbc.update("UPDATE page SET parent = 999 WHERE id = 101");
        assertFalse(permissions.hasUserPermission(page, null, VIEW));
        assertThrows(IllegalStateException.class, () -> permissions.setInheriting(page, false, 12));
    }

    @Test
    void rootCannotInheritAndInactiveAccountsCannotUseGrants() {
        grant(PagePermission.RoleType.USER, 10, EDIT);
        assertThrows(IllegalStateException.class, () -> permissions.setInheriting(page, true, 12));
        jdbc.update("UPDATE user SET active = FALSE WHERE id = 10");
        assertFalse(permissions.hasUserPermission(page, 10, VIEW));
        assertFalse(permissions.hasUserPermission(page, 999, VIEW));
    }

    @Test
    void authenticatedRootIsPrivateAndUsesSessionAuditFields() {
        creationContext(UserDetails.Role.USER, false, mock(PageLabelDao.class)).run(context -> {
            PageDto dto = PageDto.builder().id(999).createdBy(11).changedBy(11).parent(0).name("private").content("text").build();
            int id = context.getBean(PageCreationService.class).create(dto, List.of());
            assertNotEquals(999, id);
            assertEquals(10, jdbc.queryForObject("SELECT created_by FROM page WHERE id = ?", Integer.class, id));
            assertEquals(10, jdbc.queryForObject("SELECT changed_by FROM page WHERE id = ?", Integer.class, id));
            assertTrue(permissions.hasUserPermission(new PageId(id), 10, EDIT));
            assertTrue(permissions.hasUserPermission(new PageId(id), 12, EDIT));
            assertFalse(permissions.hasUserPermission(new PageId(id), 11, VIEW));
            assertFalse(permissions.hasUserPermission(new PageId(id), null, VIEW));
        });
    }

    @Test
    void guestRootCreationRequiresOptInAndGrantsOnlyPublicRead() {
        creationContext(UserDetails.Role.GUEST, false, mock(PageLabelDao.class)).run(context -> {
            assertThrows(ResponseStatusException.class, () -> context.getBean(PageCreationService.class)
                    .create(PageDto.builder().name("blocked").build(), List.of()));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM page", Integer.class));
        });
        creationContext(UserDetails.Role.GUEST, true, mock(PageLabelDao.class)).run(context -> {
            int id = context.getBean(PageCreationService.class).create(
                    PageDto.builder().createdBy(12).name("public").content("text").build(), List.of());
            assertNull(jdbc.queryForObject("SELECT created_by FROM page WHERE id = ?", Integer.class, id));
            assertTrue(permissions.hasUserPermission(new PageId(id), null, VIEW));
            assertFalse(permissions.hasUserPermission(new PageId(id), null, EDIT));
        });
    }

    @Test
    void guestChildCreationNeedsEditAndInheritsSubsequentChanges() {
        grant(PagePermission.RoleType.GUEST, null, VIEW);
        creationContext(UserDetails.Role.GUEST, false, mock(PageLabelDao.class)).run(context -> {
            var service = context.getBean(PageCreationService.class);
            assertThrows(ResponseStatusException.class, () -> service.create(
                    PageDto.builder().parent(100).name("blocked").build(), List.of()));
            jdbc.update("UPDATE page_permission SET permission_level = 'edit' WHERE page_id = 100");
            int id = service.create(PageDto.builder().parent(100).name("inherited").content("text").build(), List.of());
            assertTrue(permissions.isInheriting(new PageId(id)));
            assertTrue(permissions.hasUserPermission(new PageId(id), null, VIEW));
            jdbc.update("DELETE FROM page_permission WHERE page_id = 100");
            assertFalse(permissions.hasUserPermission(new PageId(id), null, VIEW));
        });
    }

    @Test
    void creationFailureRollsBackPageAndGrants() {
        PageLabelDao labels = mock(PageLabelDao.class);
        doThrow(new DataIntegrityViolationException("test failure")).when(labels).setForPage(any(), any());
        creationContext(UserDetails.Role.USER, false, labels).run(context -> {
            assertThrows(DataIntegrityViolationException.class, () -> context.getBean(PageCreationService.class)
                    .create(PageDto.builder().name("rolled back").content("text").build(), List.of("label")));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM page", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM page_permission", Integer.class));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {}

    @Test
    void labelFailureRollsBackContentAuditAndLabelReplacement() {
        PageLabelImpl labels = spy(new PageLabelImpl(JdbcClient.create(dataSource)));
        labels.setForPage(page, List.of("original"));
        var before = jdbc.queryForMap("SELECT * FROM page WHERE id = 100");
        doAnswer(call -> {
            call.callRealMethod();
            throw new DataIntegrityViolationException("failure after replacing labels");
        }).when(labels).setForPage(page, List.of("replacement"));
        editingContext(UserDetails.Role.ADMIN, labels).run(context -> {
            assertThrows(DataIntegrityViolationException.class, () -> context.getBean(PageEditingService.class)
                    .edit(page, editDto(), List.of("replacement"), Map.of(), List.of(), List.of()));
            assertEquals(before, jdbc.queryForMap("SELECT * FROM page WHERE id = 100"));
            assertEquals(List.of("original"), labels.listForPage(page));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tag WHERE name = 'replacement'", Integer.class));
        });
    }

    @Test
    void latePermissionFailureRollsBackContentLabelsAndEveryGrantChange() {
        var labels = new PageLabelImpl(JdbcClient.create(dataSource));
        labels.setForPage(page, List.of("original"));
        grant(PagePermission.RoleType.USER, 10, VIEW);
        grant(PagePermission.RoleType.GROUP, 20, VIEW);
        var grants = permissions.listPermissionsByPageId(page);
        int updated = grants.get(0).getId();
        int deleted = grants.get(1).getId();
        var before = jdbc.queryForList("SELECT * FROM page_permission ORDER BY id");
        editingContext(UserDetails.Role.ADMIN, labels).run(context -> {
            assertThrows(ResponseStatusException.class, () -> context.getBean(PageEditingService.class).edit(
                    page, editDto(), List.of("replacement"), Map.of("" + updated, "edit"), List.of("" + deleted),
                    List.of(Map.of("roleType", "guest", "permissionLevel", "view"),
                            Map.of("roleType", "guest", "permissionLevel", "invalid"))));
            assertEquals("protected", jdbc.queryForObject("SELECT name FROM page WHERE id = 100", String.class));
            assertEquals(List.of("original"), labels.listForPage(page));
            assertEquals(before, jdbc.queryForList("SELECT * FROM page_permission ORDER BY id"));
        });
    }

    @Test
    void foreignPermissionIdRollsBackEditorSubmission() {
        var labels = new PageLabelImpl(JdbcClient.create(dataSource));
        editingContext(UserDetails.Role.ADMIN, labels).run(context -> {
            assertThrows(IllegalStateException.class, () -> context.getBean(PageEditingService.class)
                    .edit(page, editDto(), List.of("replacement"), Map.of("99999", "edit"), List.of(), List.of()));
            assertEquals("protected", jdbc.queryForObject("SELECT name FROM page WHERE id = 100", String.class));
            assertTrue(labels.listForPage(page).isEmpty());
        });
    }

    @Test
    void successfulEditCommitsTogetherAndPreservesCreationAndScheduling() {
        var labels = new PageLabelImpl(JdbcClient.create(dataSource));
        jdbc.update("UPDATE page SET time_start = '2026-01-02 03:04:05', active = FALSE WHERE id = 100");
        var original = jdbc.queryForMap("SELECT created_by, create_date, time_start, active FROM page WHERE id = 100");
        editingContext(UserDetails.Role.ADMIN, labels).run(context -> {
            context.getBean(PageEditingService.class).edit(page, editDto(), List.of("replacement"), Map.of(), List.of(),
                    List.of(Map.of("roleType", "guest", "permissionLevel", "view")));
            assertEquals("updated", jdbc.queryForObject("SELECT name FROM page WHERE id = 100", String.class));
            assertEquals(12, jdbc.queryForObject("SELECT changed_by FROM page WHERE id = 100", Integer.class));
            assertEquals(original, jdbc.queryForMap("SELECT created_by, create_date, time_start, active FROM page WHERE id = 100"));
            assertEquals(List.of("replacement"), labels.listForPage(page));
            var grant = permissions.listPermissionsByPageId(page).getFirst();
            assertEquals(12, grant.getCreatedBy());
            assertEquals(12, grant.getChangedBy());
        });
    }

    @Test
    void guestEditAndRestUpdateUseActualAuditIdentity() {
        var labels = new PageLabelImpl(JdbcClient.create(dataSource));
        grant(PagePermission.RoleType.GUEST, null, EDIT);
        editingContext(UserDetails.Role.GUEST, labels).run(context -> {
            context.getBean(PageEditingService.class).edit(page, editDto(), List.of(), Map.of(), List.of(), List.of());
            assertNull(jdbc.queryForObject("SELECT changed_by FROM page WHERE id = 100", Integer.class));
        });
        editingContext(UserDetails.Role.USER, labels).run(context -> {
            var result = context.getBean(PageEditingService.class).update(page, editDto());
            assertEquals(10, result.getChangedBy());
            assertEquals(12, result.getCreatedBy());
            assertEquals(10, jdbc.queryForObject("SELECT changed_by FROM page WHERE id = 100", Integer.class));
        });
    }

    private PageDto editDto() {
        return PageDto.builder().id(999).name("updated").content("<p>new text</p>")
                .createdBy(999).changedBy(999).active(true).deleted(false).build();
    }

    private ApplicationContextRunner editingContext(UserDetails.Role role, PageLabelDao labels) {
        UserContext users = mock(UserContext.class);
        when(users.getUserContext()).thenReturn(UserDetails.builder()
                .userId(role == UserDetails.Role.ADMIN ? "12" : "10").role(role).build());
        return new ApplicationContextRunner().withUserConfiguration(Transactions.class)
                .withBean("transactionManager", DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(dataSource))
                .withBean(PageEditingService.class, () -> new PageEditingService(
                        new PageImpl(mock(FileDao.class), JdbcClient.create(dataSource)), permissions, labels, users));
    }

    private ApplicationContextRunner creationContext(UserDetails.Role role, boolean allowGuestRoot, PageLabelDao labels) {
        UserContext users = mock(UserContext.class);
        when(users.getUserContext()).thenReturn(UserDetails.builder().userId("10").login("reader").role(role).build());
        return new ApplicationContextRunner().withUserConfiguration(Transactions.class)
                .withPropertyValues("knowledgeroot.pages.allow-guest-root-creation=" + allowGuestRoot)
                .withBean("transactionManager", DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(dataSource))
                .withBean(PageCreationService.class, () -> new PageCreationService(
                        new PageImpl(mock(FileDao.class), JdbcClient.create(dataSource)), permissions, labels, users));
    }

    private void grant(PagePermission.RoleType type, Integer roleId, PagePermission.PermissionLevel level) {
        LocalDateTime now = LocalDateTime.now();
        permissions.createPermission(PagePermission.builder().pageId(page).roleType(type).roleId(roleId)
                .permissionLevel(level).createdBy(12).changedBy(12).createDate(now).changeDate(now).build());
    }
}
