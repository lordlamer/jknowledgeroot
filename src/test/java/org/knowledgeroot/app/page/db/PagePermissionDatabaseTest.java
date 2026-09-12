package org.knowledgeroot.app.page.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.config.LiquibaseConfig;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PageCreationService;
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
