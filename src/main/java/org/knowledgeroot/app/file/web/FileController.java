package org.knowledgeroot.app.file.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
@Slf4j
class FileController {
    private final FileDao fileDao;
    private final PagePermissionDao pagePermissionDao;
    private final UserContext userContext;

    private Integer getCurrentUserId() {
        UserDetails currentUser = userContext.getUserContext();
        if (currentUser.isGuest()) {
            return null;
        }
        try {
            return Integer.valueOf(currentUser.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean hasPagePermission(Integer pageId, PagePermission.PermissionLevel level) {
        return pagePermissionDao.hasUserPermission(new PageId(pageId), getCurrentUserId(), level);
    }

    /**
     * Upload a file to the server.
     */
    @PostMapping(
            value = "/ui/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public @ResponseBody ModelAndView uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pageId") Integer pageId) {
        try {
            if (file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
            }

            if (!hasPagePermission(pageId, PagePermission.PermissionLevel.EDIT)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to upload files for this page");
            }

            fileDao.createFile(file, pageId);
            return new ModelAndView("redirect:/ui/page/" + pageId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload file: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not upload file: " + e.getMessage());
        }
    }

    /**
     * Download a file from the server.
     */
    @GetMapping("/ui/file/{id}/download/{filename}")
    public void downloadFile(
            HttpServletResponse response,
            @PathVariable("id") Integer fileId,
            @PathVariable("filename") String filename
    ) {
        File meta;
        try {
            meta = fileDao.findById(fileId);
        } catch (EmptyResultDataAccessException e) {
            writeError(response, HttpStatus.NOT_FOUND, "File not found");
            return;
        }

        if (!hasPagePermission(meta.getPageId(), PagePermission.PermissionLevel.VIEW)) {
            writeError(response, HttpStatus.FORBIDDEN, "Forbidden");
            return;
        }

        try (InputStream inputStream = fileDao.loadFile(fileId)) {
            if (inputStream == null) {
                writeError(response, HttpStatus.NOT_FOUND, "File not found");
                return;
            }

            response.setContentType(meta.getType() == null ? "application/octet-stream" : meta.getType());
            response.setHeader(
                    "Content-Disposition",
                    String.format("attachment; filename=\"%s\"", meta.getName() == null ? "download" : meta.getName())
            );

            FileCopyUtils.copy(inputStream, response.getOutputStream());
        } catch (Exception e) {
            log.error("Failed to download file: {}", e.getMessage(), e);
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, "Could not download file!");
        }
    }

    /**
     * Delete a file from the server.
     */
    @DeleteMapping("/ui/file/{id}")
    public @ResponseBody void deleteFile(@PathVariable("id") Integer fileId) {
        File meta;
        try {
            meta = fileDao.findById(fileId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }

        if (!hasPagePermission(meta.getPageId(), PagePermission.PermissionLevel.EDIT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to delete this file");
        }

        fileDao.deleteFileById(fileId);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) {
        try {
            response.setContentType("text/plain");
            response.setStatus(status.value());
            response.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().close();
        } catch (Exception ex) {
            log.error("Failed to send error response: {}", ex.getMessage(), ex);
        }
    }
}
