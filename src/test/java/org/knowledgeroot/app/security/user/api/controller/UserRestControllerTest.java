package org.knowledgeroot.app.security.user.api.controller;

import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.security.auth.PasswordHasher;
import org.knowledgeroot.app.security.user.api.dto.UserDto;
import org.knowledgeroot.app.security.user.domain.User;
import org.knowledgeroot.app.security.user.domain.UserDao;
import org.knowledgeroot.app.security.user.domain.UserId;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class UserRestControllerTest {

    @Test
    void createUserShouldRejectMissingPassword() {
        UserDao userDao = mock(UserDao.class);
        UserRestController controller = new UserRestController(userDao);

        UserDto dto = new UserDto();
        dto.setLogin("new-user");
        dto.setPassword("   ");

        var response = controller.createUser(dto, UriComponentsBuilder.newInstance());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(userDao, never()).createUser(any());
    }

    @Test
    void createUserShouldHashPasswordBeforePersisting() {
        UserDao userDao = mock(UserDao.class);
        when(userDao.isUserExist(any(User.class))).thenReturn(false);
        UserRestController controller = new UserRestController(userDao);

        UserDto dto = new UserDto();
        dto.setLogin("new-user");
        dto.setPassword("my-plain-password");

        var response = controller.createUser(dto, UriComponentsBuilder.newInstance());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDao).createUser(captor.capture());
        assertTrue(PasswordHasher.verify("my-plain-password", captor.getValue().getPassword()));
    }

    @Test
    void updateUserShouldKeepExistingPasswordWhenMaskedValueIsSent() {
        UserDao userDao = mock(UserDao.class);
        UserRestController controller = new UserRestController(userDao);

        String existingHash = PasswordHasher.hash("existing-password", PasswordHasher.HASH_METHOD.SHA256, 1000);
        User existingUser = User.builder()
                .id(new UserId(5))
                .password(existingHash)
                .build();
        when(userDao.findById(any(UserId.class))).thenReturn(existingUser);

        UserDto dto = new UserDto();
        dto.setId(5);
        dto.setPassword("***");

        var response = controller.updateUser(5, dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDao).updateUser(captor.capture());
        assertEquals(existingHash, captor.getValue().getPassword());
    }

    @Test
    void updateUserShouldHashPasswordWhenNewPasswordIsProvided() {
        UserDao userDao = mock(UserDao.class);
        UserRestController controller = new UserRestController(userDao);

        String existingHash = PasswordHasher.hash("existing-password", PasswordHasher.HASH_METHOD.SHA256, 1000);
        User existingUser = User.builder()
                .id(new UserId(9))
                .password(existingHash)
                .build();
        when(userDao.findById(any(UserId.class))).thenReturn(existingUser);

        UserDto dto = new UserDto();
        dto.setId(9);
        dto.setPassword("new-password");

        var response = controller.updateUser(9, dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDao).updateUser(captor.capture());
        assertTrue(PasswordHasher.verify("new-password", captor.getValue().getPassword()));
    }
}
