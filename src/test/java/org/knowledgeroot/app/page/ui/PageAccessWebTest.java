package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.knowledgeroot.app.page.api.PageRestController;
import org.knowledgeroot.app.page.domain.*;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.user.domain.GroupDao;
import org.knowledgeroot.app.security.user.domain.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.knowledgeroot.app.page.domain.PagePermission.PermissionLevel.EDIT;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PageController.class, PageRestController.class})
@Import({WebSecurityConfig.class, PageCreationService.class, PageEditingService.class})
class PageAccessWebTest {
    @Autowired MockMvc mvc;
    @Autowired PageCreationService creation;
    @MockitoBean PageDao pages;
    @MockitoBean PagePermissionDao permissions;
    @MockitoBean PageStarDao stars;
    @MockitoBean PageCommentDao comments;
    @MockitoBean PageLabelDao labels;
    @MockitoBean UserDao users;
    @MockitoBean GroupDao groups;
    @MockitoBean UserContext userContext;
    @MockitoBean DatabaseAuthentificationProvider provider;
    private RequestPostProcessor caller;
    private final PageId pageId = new PageId(100);

    @BeforeEach
    void setUp() {
        as(UserDetails.Role.USER);
        ReflectionTestUtils.setField(creation, "allowGuestRootCreation", false);
        when(pages.findById(pageId)).thenReturn(Page.builder().pageId(pageId).parent(10)
                .name("page").content("text").active(true).deleted(false).build());
        when(permissions.permissionSource(pageId)).thenReturn(pageId);
        when(pages.createPage(any())).thenReturn(123);
    }

    @Test
    void guestCannotOpenOrSubmitRootCreationByDefault() throws Exception {
        as(UserDetails.Role.GUEST);
        mvc.perform(get("/ui/page/new").with(caller)).andExpect(status().isForbidden());
        mvc.perform(post("/ui/page/new").with(caller).with(csrf()).param("revision", "0").param("name", "blocked"))
                .andExpect(status().isForbidden());
        verify(pages, never()).createPage(any());
    }

    @Test
    void optedInGuestCreationUsesAnonymousAuditAndDefaultPolicy() throws Exception {
        as(UserDetails.Role.GUEST);
        ReflectionTestUtils.setField(creation, "allowGuestRootCreation", true);
        mvc.perform(post("/ui/page/new").with(caller).with(csrf())
                        .param("revision", "0").param("name", "public").param("createdBy", "999"))
                .andExpect(redirectedUrl("/ui/page/123?trigger=reload-sidebar"));
        verify(pages).createPage(argThat(page -> page.getCreatedBy() == null && page.getParent() == 0));
        verify(permissions).createDefaultPermissions(new PageId(123), null);
    }

    @Test
    void authenticatedRootCreationUsesSessionIdentity() throws Exception {
        mvc.perform(post("/ui/page/new").with(caller).with(csrf()).param("revision", "0").param("name", "private")
                        .param("id", "999").param("createdBy", "999"))
                .andExpect(redirectedUrl("/ui/page/123?trigger=reload-sidebar"));
        verify(pages).createPage(argThat(page -> page.getPageId() == null && page.getCreatedBy() == 2));
        verify(permissions).createDefaultPermissions(new PageId(123), 2);
    }

    @ParameterizedTest
    @EnumSource(value = UserDetails.Role.class, names = {"USER", "GUEST"})
    void childFormAndSubmissionRequireParentEdit(UserDetails.Role role) throws Exception {
        as(role);
        when(permissions.hasUserPermission(pageId, role == UserDetails.Role.GUEST ? null : 2, EDIT)).thenReturn(false);
        mvc.perform(get("/ui/page/100/new").with(caller)).andExpect(status().isForbidden());
        mvc.perform(post("/ui/page/new").with(caller).with(csrf()).param("parent", "100").param("revision", "0").param("name", "child"))
                .andExpect(status().isForbidden());
        verify(pages, never()).createPage(any());
    }

    @Test
    void guestWithParentEditMayCreateChildWithoutRootOptIn() throws Exception {
        as(UserDetails.Role.GUEST);
        when(permissions.hasUserPermission(pageId, null, EDIT)).thenReturn(true);
        mvc.perform(post("/ui/page/new").with(caller).with(csrf()).param("parent", "100").param("revision", "0").param("name", "child"))
                .andExpect(redirectedUrl("/ui/page/123?trigger=reload-sidebar"));
        verify(pages).createPage(argThat(page -> page.getParent() == 100 && page.getCreatedBy() == null));
        verify(permissions).createDefaultPermissions(new PageId(123), null);
    }

    @Test
    void restCreationUsesGeneratedIdAndSameDefaultRights() throws Exception {
        as(UserDetails.Role.ADMIN);
        mvc.perform(post("/page").with(caller).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":999,\"createdBy\":999,\"name\":\"api page\",\"content\":\"text\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost/page/123"));
        verify(permissions).createDefaultPermissions(new PageId(123), 1);
        verify(pages).createPage(argThat(page -> page.getPageId() == null && page.getCreatedBy() == 1));
    }

    @ParameterizedTest
    @EnumSource(value = UserDetails.Role.class, names = {"USER", "GUEST"})
    void editingRightsDoNotAllowManagingPermissions(UserDetails.Role role) throws Exception {
        as(role);
        mvc.perform(get("/ui/page/100/permissions").with(caller)).andExpect(status().isForbidden());
        mvc.perform(post("/ui/page/100/permission").with(caller).with(csrf())
                        .param("roleType", "guest").param("permissionLevel", "view"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/ui/page/100/permission/9").with(caller).with(csrf()).param("permissionLevel", "edit"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/ui/page/100/permission/9").with(caller).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/ui/page/100/permission-mode").with(caller).with(csrf()).param("inherit", "false"))
                .andExpect(status().isForbidden());
        verify(permissions, never()).setInheriting(any(), anyBoolean(), any());
        verify(permissions, never()).createPermission(any());
        verify(permissions, never()).listPermissionsByPageId(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"permissionUpdates[9]", "permissionDeletions[]", "permissionAdditions[0][roleType]"})
    void forgedPermissionFieldsRejectEntireEditorSubmission(String key) throws Exception {
        mvc.perform(post("/ui/page/100/edit").with(caller).with(csrf())
                        .param("revision", "0").param("name", "must not save").param(key, "guest"))
                .andExpect(status().isForbidden());
        verify(pages, never()).updatePage(any());
        verify(labels, never()).setForPage(any(), any());
    }

    @Test
    void normalEditorCanSaveContentButDoesNotReceivePermissionControls() throws Exception {
        mvc.perform(get("/ui/page/100/edit").with(caller).header("HX-Request", "true"))
                .andExpect(status().isOk()).andExpect(model().attribute("canManagePermissions", false))
                .andExpect(content().string(not(containsString("id=\"permissions-content\""))));
        verify(permissions, never()).listPermissionsByPageId(any());
        mvc.perform(post("/ui/page/100/edit").with(caller).with(csrf()).param("revision", "0").param("name", "updated"))
                .andExpect(status().is3xxRedirection());
        verify(pages).updatePage(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankNamesAreRejectedWithoutWriting(String name) throws Exception {
        mvc.perform(post("/ui/page/100/edit").with(caller).with(csrf()).param("revision", "0").param("name", name))
                .andExpect(status().isBadRequest());
        verify(pages, never()).updatePage(any());
    }

    @Test
    void oversizedPageInputIsRejectedBeforeCreation() throws Exception {
        mvc.perform(post("/ui/page/new").with(caller).with(csrf()).param("revision", "0").param("name", "x".repeat(256)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/ui/page/new").with(caller).with(csrf()).param("revision", "0").param("name", "valid").param("content", "x".repeat(65536)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/ui/page/new").with(caller).with(csrf()).param("revision", "0").param("name", "valid").param("labels", "x".repeat(65)))
                .andExpect(status().isBadRequest());
        verify(pages, never()).createPage(any());
    }

    @Test
    void editorProcessesPermissionAdditionsWithGapsInFormIndices() throws Exception {
        as(UserDetails.Role.ADMIN);
        mvc.perform(post("/ui/page/100/edit").with(caller).with(csrf()).param("revision", "0").param("name", "updated")
                        .param("permissionAdditions[3][roleType]", "guest")
                        .param("permissionAdditions[3][permissionLevel]", "view"))
                .andExpect(status().is3xxRedirection());
        verify(permissions).createPermission(argThat(grant -> grant.getRoleType() == PagePermission.RoleType.GUEST
                && grant.getRoleId() == null && grant.getCreatedBy() == 1));
    }

    @Test
    void restUpdateDiscardsForgedAuditAndReturnsStoredCreationIdentity() throws Exception {
        as(UserDetails.Role.ADMIN);
        when(pages.findById(pageId)).thenReturn(Page.builder().pageId(pageId).name("old")
                .createdBy(42).createDate(java.time.LocalDateTime.of(2020, 1, 1, 0, 0)).build());
        mvc.perform(put("/page/100").with(caller).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":100,"revision":0,"name":"updated","content":"text","active":true,"deleted":false,
                                 "createdBy":999,"changedBy":999,"changeDate":"2000-01-01T00:00:00"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdBy").value(42))
                .andExpect(jsonPath("$.changedBy").value(1));
        verify(pages).updatePage(argThat(page -> page.getChangedBy() == 1 && page.getCreatedBy() == 42
                && page.getChangeDate().getYear() > 2000));
    }

    @Test
    void administratorMustExplicitlyLeaveInheritanceBeforeEditingGrants() throws Exception {
        as(UserDetails.Role.ADMIN);
        when(permissions.isInheriting(pageId)).thenReturn(true);
        when(permissions.permissionSource(pageId)).thenReturn(new PageId(10));
        mvc.perform(get("/ui/page/100/edit").with(caller).header("HX-Request", "true"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Eigene Rechte verwenden")))
                .andExpect(content().string(not(containsString("id=\"role-type\""))));
        mvc.perform(put("/ui/page/100/permission/9").with(caller).with(csrf()).param("permissionLevel", "edit"))
                .andExpect(status().isConflict());
        mvc.perform(post("/ui/page/100/edit").with(caller).with(csrf())
                        .param("revision", "0").param("name", "must not save").param("permissionUpdates[9]", "edit"))
                .andExpect(status().isConflict());
        verify(pages, never()).updatePage(any());
        mvc.perform(post("/ui/page/100/permission-mode").with(caller).with(csrf()).param("inherit", "false"))
                .andExpect(redirectedUrl("/ui/page/100/edit"));
        verify(permissions).setInheriting(pageId, false, 1);
    }

    @Test
    void administratorCanUpdateLocalPermissionsAndSeeControls() throws Exception {
        as(UserDetails.Role.ADMIN);
        mvc.perform(get("/ui/page/100/permissions").with(caller).header("HX-Request", "true"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("id=\"role-type\"")));
        mvc.perform(put("/ui/page/100/permission/9").with(caller).with(csrf()).param("permissionLevel", "edit"))
                .andExpect(status().isOk());
        verify(permissions).updatePermissionForPage(eq(pageId), argThat(permission -> permission.getId() == 9));
    }

    @Test
    void userAndGroupPickersAreRestrictedToAdministrators() throws Exception {
        mvc.perform(get("/api/users").with(caller)).andExpect(status().isForbidden());
        mvc.perform(get("/api/groups").with(caller)).andExpect(status().isForbidden());
        verifyNoInteractions(users, groups);
    }

    private void as(UserDetails.Role role) {
        int id = role == UserDetails.Role.ADMIN ? 1 : 2;
        when(userContext.getUserContext()).thenReturn(UserDetails.builder()
                .userId(String.valueOf(id)).login("tester").role(role).build());
        caller = role == UserDetails.Role.GUEST ? anonymous() : user("tester").roles(role.name());
        when(permissions.hasUserPermission(pageId, role == UserDetails.Role.GUEST ? null : id, EDIT)).thenReturn(true);
    }
}
