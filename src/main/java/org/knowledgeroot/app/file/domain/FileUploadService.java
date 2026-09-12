package org.knowledgeroot.app.file.domain;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FileUploadService {
    private final FileDao files;
    private final PagePermissionDao permissions;
    private final UserContext users;
    private final UploadPolicy policy;

    @Transactional
    public void upload(Integer pageId, MultipartFile... uploads) {
        org.knowledgeroot.app.util.RequestValidation.id(pageId);
        var user = users.getUserContext();
        Integer actor = user.isGuest() ? null : Integer.valueOf(user.getUserId());
        if (!permissions.hasUserPermission(new PageId(pageId), actor, PagePermission.PermissionLevel.EDIT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        policy.validate(uploads);
        // Store content first; publish all metadata in one database commit.
        // Do not delete shared hash objects on rollback: another upload may already reference them.
        for (var upload : uploads) files.createFile(upload, pageId, actor);
    }
}
