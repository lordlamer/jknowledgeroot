package org.knowledgeroot.app.file.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
class FileRestController {
    private final FileDao fileImpl;
    private final PagePermissionDao pagePermissionDao;
    private final UserContext userContext;

    private final FileDtoConverter fileDtoConverter = new FileDtoConverter();

    private Integer getCurrentUserId() {
        UserDetails currentUser = userContext.getUserContext();
        if (currentUser == null || currentUser.isGuest()) {
            return null;
        }
        try {
            return Integer.valueOf(currentUser.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean hasPermission(Integer pageId, PagePermission.PermissionLevel minLevel) {
        return pagePermissionDao.hasUserPermission(new PageId(pageId), getCurrentUserId(), minLevel);
    }

    /**
     * Get files (optionally filtered by id or page_id).
     */
    @RequestMapping(value = "/file", method = org.springframework.web.bind.annotation.RequestMethod.GET)
    public ResponseEntity<List<FileDto>> listAllFiles(
            @RequestParam(name = "id", required = false) Integer id,
            @RequestParam(name = "page_id", required = false) Integer pageId
    ) {
        try {
            List<File> files;

            if (id != null) {
                File file = fileImpl.findById(id);
                files = List.of(file);
            } else {
                FileFilter filter = FileFilter.builder()
                        .pageId(pageId == null ? null : new PageId(pageId))
                        .build();
                files = fileImpl.listFiles(filter);
            }

            List<FileDto> fileDtos = files.stream()
                    .filter(file -> hasPermission(file.getPageId(), PagePermission.PermissionLevel.VIEW))
                    .map(fileDtoConverter::convertAtoB)
                    .toList();

            if (id != null && !files.isEmpty() && fileDtos.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.FORBIDDEN);
            }

            if (fileDtos.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }

            return new ResponseEntity<>(fileDtos, HttpStatus.OK);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Get single file metadata by id.
     */
    @RequestMapping(value = "/file/{id}", method = org.springframework.web.bind.annotation.RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FileDto> getFile(@PathVariable("id") long id) {
        File file;
        try {
            file = fileImpl.findById(id);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if (!hasPermission(file.getPageId(), PagePermission.PermissionLevel.VIEW)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        return new ResponseEntity<>(fileDtoConverter.convertAtoB(file), HttpStatus.OK);
    }

    /**
     * Download file content.
     */
    @GetMapping(value = {"/file/{id}/download/{filename}", "/download/{id}/{filename}"})
    public void downloadFile(
            HttpServletResponse response,
            @PathVariable("id") Integer fileId,
            @PathVariable("filename") String filename
    ) {
        File meta;
        try {
            meta = fileImpl.findById(fileId);
        } catch (EmptyResultDataAccessException e) {
            writeError(response, HttpStatus.NOT_FOUND, "File not found");
            return;
        }

        if (!hasPermission(meta.getPageId(), PagePermission.PermissionLevel.VIEW)) {
            writeError(response, HttpStatus.FORBIDDEN, "Forbidden");
            return;
        }

        try (InputStream inputStream = fileImpl.loadFile(fileId)) {
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
            log.error("Could not download file {}: {}", fileId, e.getMessage(), e);
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, "Could not download file!");
        }
    }

    /**
     * Create files for a page.
     */
    @PostMapping(value = "/file")
    public ResponseEntity<Void> createFile(
            @RequestParam("file") MultipartFile[] files,
            @RequestParam Integer parentContent,
            UriComponentsBuilder ucBuilder
    ) {
        if (!hasPermission(parentContent, PagePermission.PermissionLevel.EDIT)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            fileImpl.createFile(file, parentContent);
        }

        HttpHeaders headers = new HttpHeaders();
        return new ResponseEntity<>(headers, HttpStatus.CREATED);
    }

    /**
     * Delete file by id.
     */
    @DeleteMapping(value = "/file/{id}")
    public ResponseEntity<FileDto> deleteFile(@PathVariable("id") long id) {
        File file;
        try {
            file = fileImpl.findById(id);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if (!hasPermission(file.getPageId(), PagePermission.PermissionLevel.EDIT)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        fileImpl.deleteFileById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Dangerous bulk endpoint is disabled.
     */
    @DeleteMapping(value = "/file")
    public ResponseEntity<FileDto> deleteAllFiles() {
        return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) {
        try {
            OutputStream outputStream = response.getOutputStream();
            response.setStatus(status.value());
            response.setContentType("text/plain");
            outputStream.write(message.getBytes(StandardCharsets.UTF_8));
            outputStream.close();
        } catch (Exception ex) {
            log.error("Could not write error response: {}", ex.getMessage(), ex);
        }
    }
}