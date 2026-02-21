package org.knowledgeroot.app.file.web;

import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileControllerTest {

    @Test
    void uploadFileShouldReturnForbiddenWithoutEditPermission() {
        FileDao fileDao = mock(FileDao.class);
        PagePermissionDao pagePermissionDao = mock(PagePermissionDao.class);
        UserContext userContext = mock(UserContext.class);
        when(userContext.getUserContext()).thenReturn(guestUser());
        when(pagePermissionDao.hasUserPermission(any(PageId.class), any(), eq(PagePermission.PermissionLevel.EDIT)))
                .thenReturn(false);

        FileController controller = new FileController(fileDao, pagePermissionDao, userContext);

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "abc".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.uploadFile(multipartFile, 10)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fileDao, never()).createFile(any(), any());
    }

    @Test
    void downloadFileShouldReturnForbiddenWithoutViewPermission() throws Exception {
        FileDao fileDao = mock(FileDao.class);
        PagePermissionDao pagePermissionDao = mock(PagePermissionDao.class);
        UserContext userContext = mock(UserContext.class);
        when(userContext.getUserContext()).thenReturn(guestUser());

        File meta = File.builder()
                .id(1)
                .pageId(55)
                .name("secret.txt")
                .type("text/plain")
                .build();
        when(fileDao.findById(1)).thenReturn(meta);
        when(pagePermissionDao.hasUserPermission(any(PageId.class), any(), eq(PagePermission.PermissionLevel.VIEW)))
                .thenReturn(false);

        FileController controller = new FileController(fileDao, pagePermissionDao, userContext);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.downloadFile(response, 1, "secret.txt");

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        assertTrue(response.getContentAsString().contains("Forbidden"));
        verify(fileDao, never()).loadFile(any());
    }

    @Test
    void deleteFileShouldReturnForbiddenWithoutEditPermission() {
        FileDao fileDao = mock(FileDao.class);
        PagePermissionDao pagePermissionDao = mock(PagePermissionDao.class);
        UserContext userContext = mock(UserContext.class);
        when(userContext.getUserContext()).thenReturn(guestUser());

        File meta = File.builder()
                .id(7)
                .pageId(77)
                .name("locked.txt")
                .build();
        when(fileDao.findById(7)).thenReturn(meta);
        when(pagePermissionDao.hasUserPermission(any(PageId.class), any(), eq(PagePermission.PermissionLevel.EDIT)))
                .thenReturn(false);

        FileController controller = new FileController(fileDao, pagePermissionDao, userContext);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.deleteFile(7));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fileDao, never()).deleteFileById(anyLong());
    }

    private UserDetails guestUser() {
        return UserDetails.builder()
                .userId("guest")
                .login("guest")
                .email("")
                .firstName("Guest")
                .surName("")
                .role(UserDetails.Role.GUEST)
                .language("en")
                .build();
    }
}
