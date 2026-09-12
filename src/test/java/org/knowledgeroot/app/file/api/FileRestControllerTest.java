package org.knowledgeroot.app.file.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.auth.DatabaseAuthentificationProvider;
import org.knowledgeroot.app.security.config.WebSecurityConfig;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.knowledgeroot.app.page.domain.PagePermission.PermissionLevel.VIEW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileRestController.class)
@Import({WebSecurityConfig.class, org.knowledgeroot.app.file.domain.FileUploadService.class, org.knowledgeroot.app.file.domain.UploadPolicy.class})
class FileRestControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private FileDao files;
    @MockitoBean private PagePermissionDao permissions;
    @MockitoBean private UserContext userContext;
    @MockitoBean private DatabaseAuthentificationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        when(userContext.getUserContext()).thenReturn(UserDetails.builder()
                .userId("2").login("reader").role(UserDetails.Role.USER).build());
    }

    @Test
    void batchUploadRejectsEmptyLaterFileBeforeSavingAnything() throws Exception {
        when(userContext.getUserContext()).thenReturn(UserDetails.builder()
                .userId("2").login("admin").role(UserDetails.Role.ADMIN).build());
        when(permissions.hasUserPermission(new PageId(10), 2,
                org.knowledgeroot.app.page.domain.PagePermission.PermissionLevel.EDIT)).thenReturn(true);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/file")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "valid.txt", "text/plain", new byte[]{1}))
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "empty.txt", "text/plain", new byte[]{}))
                        .param("parentContent", "10").with(user("admin").roles("ADMIN"))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
        verify(files, never()).createFile(any(), any(), any());
    }

    @Test
    void uploadPassesSessionIdentityToMetadataWriter() throws Exception {
        when(userContext.getUserContext()).thenReturn(UserDetails.builder()
                .userId("2").login("admin").role(UserDetails.Role.ADMIN).build());
        when(permissions.hasUserPermission(new PageId(10), 2,
                org.knowledgeroot.app.page.domain.PagePermission.PermissionLevel.EDIT)).thenReturn(true);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/file")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "valid.txt", "text/plain", new byte[]{1}))
                        .param("parentContent", "10").param("createdBy", "999").with(user("admin").roles("ADMIN"))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated());
        verify(files).createFile(any(), eq(10), eq(2));
    }

    @Test
    void listDoesNotExposeFilesOnPrivatePages() throws Exception {
        when(files.listFiles(any(FileFilter.class))).thenReturn(List.of(file(1, 10), file(7, 99)));
        when(permissions.hasUserPermission(new PageId(10), 2, VIEW)).thenReturn(true);

        mvc.perform(get("/file").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(content().string(not(containsString("file-7.txt"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/file/7", "/file?id=7", "/file/7/download/ignored.txt", "/download/7/ignored.txt"})
    void metadataAndDownloadAliasesRespectTheFilesActualPage(String path) throws Exception {
        when(files.findById(7)).thenReturn(file(7, 99));

        mvc.perform(get(path).with(user("reader")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("file-7.txt"))))
                .andExpect(header().doesNotExist("Content-Disposition"));

        verify(permissions).hasUserPermission(new PageId(99), 2, VIEW);
        verify(files, never()).loadFile(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/file/7/download/ignored.txt", "/download/7/ignored.txt"})
    void permittedDownloadRemainsUsable(String path) throws Exception {
        when(files.findById(7)).thenReturn(file(7, 99));
        when(permissions.hasUserPermission(new PageId(99), 2, VIEW)).thenReturn(true);
        when(files.loadFile(7)).thenReturn(new ByteArrayInputStream("contents".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get(path).with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(content().string("contents"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"file-7.txt\"; filename*=UTF-8''file-7.txt"));
    }

    private File file(int id, int pageId) {
        return File.builder().id(id).pageId(pageId).name("file-" + id + ".txt").type("text/plain").build();
    }
}
