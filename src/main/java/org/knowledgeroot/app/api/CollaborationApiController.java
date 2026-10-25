package org.knowledgeroot.app.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.knowledgeroot.app.file.domain.FileUploadService;
import org.knowledgeroot.app.file.web.DownloadHeaders;
import org.knowledgeroot.app.page.domain.PageCommentDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Comments and attachments of a page. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class CollaborationApiController {
    private static final int MAX_COMMENT_LENGTH = 4000;
    private final ApiSupport api;
    private final PageCommentDao comments;
    private final FileDao files;
    private final FileUploadService uploads;

    @GetMapping("/pages/{id}/comments")
    List<ApiModels.Comment> comments(@PathVariable int id) {
        api.require(id, PagePermission.PermissionLevel.VIEW);
        return list(id);
    }

    @PostMapping("/pages/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    List<ApiModels.Comment> comment(@PathVariable int id, @RequestBody ApiModels.CommentInput input) {
        int user = api.requireUser();
        api.require(id, PagePermission.PermissionLevel.VIEW);
        String text = input == null || input.content() == null ? "" : input.content().trim();
        if (text.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a comment first.");
        if (text.length() > MAX_COMMENT_LENGTH)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comments are limited to 4000 characters.");
        comments.create(new PageId(id), user, text);
        return list(id);
    }

    @DeleteMapping("/pages/{id}/comments/{commentId}")
    List<ApiModels.Comment> deleteComment(@PathVariable int id, @PathVariable int commentId) {
        int user = api.requireUser();
        api.require(id, PagePermission.PermissionLevel.VIEW);
        var comment = comments.findById(commentId)
                .filter(found -> found.getPageId().value() == id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found."));
        boolean own = comment.getUserId() != null && comment.getUserId() == user;
        if (!own && !api.admin())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author or an administrator can delete this comment.");
        comments.delete(commentId);
        return list(id);
    }

    @GetMapping("/pages/{id}/files")
    List<ApiModels.FileInfo> files(@PathVariable int id) {
        api.require(id, PagePermission.PermissionLevel.VIEW);
        return attachments(id);
    }

    /** Multipart upload of one or more {@code file} parts within the server's upload limits. */
    @PostMapping(value = "/pages/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    List<ApiModels.FileInfo> upload(@PathVariable int id, @RequestParam("file") MultipartFile[] parts) {
        api.require(id, PagePermission.PermissionLevel.EDIT);
        uploads.upload(id, parts);
        return attachments(id);
    }

    @DeleteMapping("/files/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteFile(@PathVariable int fileId) {
        File file = files.findById(fileId);
        api.require(file.getPageId(), PagePermission.PermissionLevel.EDIT);
        files.deleteFileById(fileId);
    }

    @GetMapping("/files/{fileId}/content")
    void download(@PathVariable int fileId, HttpServletResponse response) throws IOException {
        File file = files.findById(fileId);
        api.require(file.getPageId(), PagePermission.PermissionLevel.VIEW);
        try (InputStream content = files.loadFile(fileId)) {
            if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found.");
            DownloadHeaders.apply(response, file);
            FileCopyUtils.copy(content, response.getOutputStream());
        }
    }

    private List<ApiModels.Comment> list(int pageId) {
        Integer viewer = api.viewer();
        boolean admin = api.admin();
        return comments.listForPage(new PageId(pageId)).stream()
                .map(comment -> ApiModels.Comment.of(comment, viewer, admin)).toList();
    }

    private List<ApiModels.FileInfo> attachments(int pageId) {
        return files.listFiles(FileFilter.builder().pageId(new PageId(pageId)).build()).stream()
                .map(ApiModels.FileInfo::of).toList();
    }
}
