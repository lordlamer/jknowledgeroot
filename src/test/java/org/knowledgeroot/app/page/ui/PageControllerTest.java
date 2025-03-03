package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.user.domain.*;
import org.knowledgeroot.app.security.user.api.filter.UserFilter;
import org.knowledgeroot.app.security.user.api.filter.GroupFilter;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

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
    private UserDao userImpl;

    @Mock
    private GroupDao groupImpl;

    private PageController pageController;

    @BeforeEach
    void setUp() {
        pageController = new PageController(pageImpl, pagePermissionImpl, userImpl, groupImpl);
    }

    @Test
    void getUsers_ShouldReturnUserList() {
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
}