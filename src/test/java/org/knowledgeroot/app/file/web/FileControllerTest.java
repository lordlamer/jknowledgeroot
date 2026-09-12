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
    void missingObjectAndUnavailableStorageHaveDifferentStatusCodes() {
        var fileDao = mock(FileDao.class);
        var permissions = mock(PagePermissionDao.class);
        var users = mock(UserContext.class);
        when(users.getUserContext()).thenReturn(guestUser());
        when(permissions.hasUserPermission(any(), any(), eq(PagePermission.PermissionLevel.VIEW))).thenReturn(true);
        when(fileDao.findById(1)).thenReturn(File.builder().id(1).pageId(1).name("file.txt").build());
        var controller = new FileController(fileDao, permissions, users, mock(org.knowledgeroot.app.file.domain.FileUploadService.class));
        when(fileDao.loadFile(1)).thenThrow(new org.knowledgeroot.app.file.domain.StoredFileNotFoundException(null));
        var missing = new MockHttpServletResponse();
        controller.downloadFile(missing, 1, "file.txt");
        assertEquals(404, missing.getStatus());
        org.mockito.Mockito.doThrow(new org.knowledgeroot.app.file.domain.StorageException("private server detail", null))
                .when(fileDao).loadFile(1);
        var unavailable = new MockHttpServletResponse();
        controller.downloadFile(unavailable, 1, "file.txt");
        assertEquals(503, unavailable.getStatus());
    }

    @Test
    void interruptedCommittedDownloadDoesNotAppendAnErrorToFileBytes() throws Exception {
        var fileDao = mock(FileDao.class);
        var permissions = mock(PagePermissionDao.class);
        var users = mock(UserContext.class);
        when(users.getUserContext()).thenReturn(guestUser());
        when(permissions.hasUserPermission(any(), any(), eq(PagePermission.PermissionLevel.VIEW))).thenReturn(true);
        when(fileDao.findById(1)).thenReturn(File.builder().id(1).pageId(1).name("file.txt").size(100000).build());
        var response = new MockHttpServletResponse();
        when(fileDao.loadFile(1)).thenReturn(new java.io.InputStream() {
            private boolean first = true;
            @Override public int read() throws java.io.IOException { throw new java.io.IOException("interrupted"); }
            @Override public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
                if (!first) {
                    response.flushBuffer();
                    throw new java.io.IOException("interrupted");
                }
                first = false;
                bytes[offset] = 42;
                return 1;
            }
        });
        var controller = new FileController(fileDao, permissions, users, mock(org.knowledgeroot.app.file.domain.FileUploadService.class));
        controller.downloadFile(response, 1, "file.txt");
        org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[]{42}, response.getContentAsByteArray());
        assertEquals("100000", response.getHeader("Content-Length"));
    }

    @Test
    void uploadFileShouldReturnForbiddenWithoutEditPermission() {
        FileDao fileDao = mock(FileDao.class);
        PagePermissionDao pagePermissionDao = mock(PagePermissionDao.class);
        UserContext userContext = mock(UserContext.class);
        when(userContext.getUserContext()).thenReturn(guestUser());
        when(pagePermissionDao.hasUserPermission(any(PageId.class), any(), eq(PagePermission.PermissionLevel.EDIT)))
                .thenReturn(false);

        FileController controller = new FileController(fileDao, pagePermissionDao, userContext, mock(org.knowledgeroot.app.file.domain.FileUploadService.class));

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
        verify(fileDao, never()).createFile(any(), any(), any());
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

        FileController controller = new FileController(fileDao, pagePermissionDao, userContext, mock(org.knowledgeroot.app.file.domain.FileUploadService.class));
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

        FileController controller = new FileController(fileDao, pagePermissionDao, userContext, mock(org.knowledgeroot.app.file.domain.FileUploadService.class));

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
