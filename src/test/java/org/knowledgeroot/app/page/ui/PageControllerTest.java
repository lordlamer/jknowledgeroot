package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageCreationService;
import org.knowledgeroot.app.page.domain.PageCommentDao;
import org.knowledgeroot.app.page.domain.PageLabelDao;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.page.domain.PageStarDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.user.domain.*;
import org.knowledgeroot.app.security.user.api.filter.UserFilter;
import org.knowledgeroot.app.security.user.api.filter.GroupFilter;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PageControllerTest {

    @Mock
    private PageDao pageImpl;

    @Mock
    private PagePermissionDao pagePermissionImpl;

    @Mock
    private PageStarDao pageStarDao;

    @Mock
    private PageCommentDao pageCommentDao;

    @Mock
    private PageLabelDao pageLabelDao;

    @Mock
    private UserDao userImpl;

    @Mock
    private GroupDao groupImpl;

    @Mock
    private UserContext userContext;
    @Mock
    private PageCreationService pageCreationService;

    private PageController pageController;

    @BeforeEach
    void setUp() {
        pageController = new PageController(pageImpl, pagePermissionImpl, pageStarDao,
                pageCommentDao, pageLabelDao, userImpl, groupImpl, userContext, pageCreationService, mock(org.knowledgeroot.app.page.domain.PageEditingService.class));
    }

    @Test
    void getUsers_ShouldReturnUserList() {
        when(userContext.getUserContext()).thenReturn(UserDetails.builder().role(UserDetails.Role.ADMIN).build());
        // Arrange
        User user1 = User.builder()
                .id(new UserId(1))
                .firstName("John")
                .lastName("Doe")
                .build();
        User user2 = User.builder()
                .id(new UserId(2))
                .firstName("Jane")
                .lastName("Smith")
                .build();
        List<User> users = Arrays.asList(user1, user2);

        when(userImpl.listUsers(any(UserFilter.class))).thenReturn(users);

        // Act
        ResponseEntity<?> response = pageController.getUsers();

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        List<Map<String, Object>> userList = (List<Map<String, Object>>) response.getBody();
        assertNotNull(userList);
        assertEquals(2, userList.size());
        assertEquals(1, userList.get(0).get("id"));
        assertEquals("John", userList.get(0).get("firstName"));
        assertEquals("Doe", userList.get(0).get("lastName"));
    }

    @Test
    void getGroups_ShouldReturnGroupList() {
        when(userContext.getUserContext()).thenReturn(UserDetails.builder().role(UserDetails.Role.ADMIN).build());
        // Arrange
        Group group1 = Group.builder()
                .id(new GroupId(1))
                .name("Administrators")
                .build();
        Group group2 = Group.builder()
                .id(new GroupId(2))
                .name("Users")
                .build();
        List<Group> groups = Arrays.asList(group1, group2);

        when(groupImpl.listGroups(any(GroupFilter.class))).thenReturn(groups);

        // Act
        ResponseEntity<?> response = pageController.getGroups();

        // Assert
        assertTrue(response.getStatusCode().is2xxSuccessful());
        List<Map<String, Object>> groupList = (List<Map<String, Object>>) response.getBody();
        assertNotNull(groupList);
        assertEquals(2, groupList.size());
        assertEquals(1, groupList.get(0).get("id"));
        assertEquals("Administrators", groupList.get(0).get("name"));
    }

    @ParameterizedTest
    @EnumSource(UserDetails.Role.class)
    void breadcrumbContainsOnlyReadableAncestors(UserDetails.Role role) {
        Integer userId = role == UserDetails.Role.GUEST ? null : 2;
        when(userContext.getUserContext()).thenReturn(UserDetails.builder()
                .userId(userId == null ? "guest" : userId.toString()).login("reader").role(role).build());
        Page root = Page.builder().pageId(new PageId(1)).name("visible-root").build();
        Page hiddenParent = Page.builder().pageId(new PageId(99)).name("secret-parent").build();
        Page child = Page.builder().pageId(new PageId(10)).name("visible-child").build();
        when(pagePermissionImpl.hasUserPermission(new PageId(10), userId, PagePermission.PermissionLevel.VIEW))
                .thenReturn(true);
        when(pagePermissionImpl.hasUserPermission(new PageId(1), userId, PagePermission.PermissionLevel.VIEW))
                .thenReturn(true);
        when(pagePermissionImpl.hasUserPermission(new PageId(99), userId, PagePermission.PermissionLevel.VIEW))
                .thenReturn(role == UserDetails.Role.ADMIN);
        when(pageImpl.findById(new PageId(10))).thenReturn(child);
        when(pageImpl.getPageHierarchy(new PageId(10))).thenReturn(List.of(root, hiddenParent, child));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = pageController.showPage(10, null, model,
                new MockHttpServletResponse(), mock(HtmxRequest.class));

        assertEquals("page/show", view);
        assertEquals(role == UserDetails.Role.ADMIN ? List.of(root, hiddenParent, child) : List.of(root, child),
                model.get("breadcrumb"));
    }
}
