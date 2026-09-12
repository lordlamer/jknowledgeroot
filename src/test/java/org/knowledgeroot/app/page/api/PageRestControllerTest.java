package org.knowledgeroot.app.page.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.knowledgeroot.app.page.domain.PagePermission.PermissionLevel.VIEW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PageRestController.class)
@Import(WebSecurityConfig.class)
class PageRestControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PageDao pages;
    @MockitoBean
    private PagePermissionDao permissions;
    @MockitoBean
    private UserContext userContext;
    @MockitoBean
    private DatabaseAuthentificationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        setCaller(2, "USER");
    }

    @Test
    void listExcludesPrivatePagesIncludingTheirMetadata() throws Exception {
        when(pages.listPages(any(PageFilter.class))).thenReturn(List.of(page(10, 0), page(99, 0)));
        when(permissions.hasUserPermission(new PageId(10), 2, VIEW)).thenReturn(true);

        mvc.perform(get("/page").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("page-10"))
                .andExpect(jsonPath("$[0].content").value("content-10"));

        verify(permissions).hasUserPermission(new PageId(99), 2, VIEW);
    }

    @Test
    void listWithoutReadablePagesReturnsNoContent() throws Exception {
        when(pages.listPages(any(PageFilter.class))).thenReturn(List.of(page(99, 0)));

        mvc.perform(get("/page").with(user("reader")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @ParameterizedTest
    @CsvSource({"/page/99", "/page?id=99"})
    void guessedPrivatePageIdIsDeniedBeforeLoadingContent(String path) throws Exception {
        mvc.perform(get(path).with(user("reader")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        verify(permissions).hasUserPermission(new PageId(99), 2, VIEW);
        verifyNoInteractions(pages);
    }

    @ParameterizedTest
    @CsvSource({"3, USER, direct-reader", "4, USER, group-member", "5, ADMIN, administrator"})
    void permittedCallersCanReadThroughTheSharedPermissionPolicy(int userId, String role, String login)
            throws Exception {
        setCaller(userId, role);
        when(permissions.hasUserPermission(new PageId(99), userId, VIEW)).thenReturn(true);
        when(pages.findById(new PageId(99))).thenReturn(page(99, 0));
        when(pages.listPages(any(PageFilter.class))).thenReturn(List.of(page(99, 0)));

        mvc.perform(get("/page/99").with(user(login).roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.content").value("content-99"));
        mvc.perform(get("/page").with(user(login).roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(99));
    }

    @ParameterizedTest
    @CsvSource({"/page", "/page/99"})
    void anonymousRequestsCannotReachTheRestApi(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        verifyNoInteractions(pages, permissions);
    }

    @Test
    void readableChildDoesNotExposePrivateParentId() throws Exception {
        when(permissions.hasUserPermission(new PageId(10), 2, VIEW)).thenReturn(true);
        Page child = page(10, 99);
        when(pages.findById(new PageId(10))).thenReturn(child);
        when(pages.listPages(any(PageFilter.class))).thenReturn(List.of(child));

        mvc.perform(get("/page/10").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parent").value(nullValue()));
        mvc.perform(get("/page").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].parent").value(nullValue()));

        org.junit.jupiter.api.Assertions.assertEquals(99, child.getParent());
    }

    @Test
    void readableParentIdIsPreserved() throws Exception {
        when(permissions.hasUserPermission(new PageId(10), 2, VIEW)).thenReturn(true);
        when(permissions.hasUserPermission(new PageId(99), 2, VIEW)).thenReturn(true);
        when(pages.findById(new PageId(10))).thenReturn(page(10, 99));

        mvc.perform(get("/page/10").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parent").value(99));
    }

    @Test
    void missingPageReturnsNotFoundWhenPermissionPolicyAllowsAccess() throws Exception {
        when(permissions.hasUserPermission(new PageId(99), 2, VIEW)).thenReturn(true);
        when(pages.findById(new PageId(99))).thenThrow(new EmptyResultDataAccessException(1));

        mvc.perform(get("/page/99").with(user("reader")))
                .andExpect(status().isNotFound());
    }

    private void setCaller(int userId, String role) {
        when(userContext.getUserContext()).thenReturn(UserDetails.builder()
                .userId(Integer.toString(userId)).login("reader")
                .role(UserDetails.Role.valueOf(role)).build());
    }

    private Page page(int id, int parent) {
        return Page.builder().pageId(new PageId(id)).parent(parent)
                .name("page-" + id).content("content-" + id).build();
    }
}
