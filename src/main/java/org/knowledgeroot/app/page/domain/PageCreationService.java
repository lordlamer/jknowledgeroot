package org.knowledgeroot.app.page.domain;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.page.api.PageDtoConverter;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PageCreationService {
    private final PageDao pages;
    private final PagePermissionDao permissions;
    private final PageLabelDao labels;
    private final UserContext users;
    @Value("${knowledgeroot.pages.allow-guest-root-creation:false}")
    private boolean allowGuestRootCreation;

    public void requireCanCreate(Integer parent) {
        if (parent != null && parent < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var user = users.getUserContext();
        Integer userId = user.isGuest() ? null : Integer.valueOf(user.getUserId());
        if (parent != null && parent > 0) {
            if (!permissions.hasUserPermission(new PageId(parent), userId, PagePermission.PermissionLevel.EDIT)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } else if (user.isGuest() && !allowGuestRootCreation) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @Transactional
    public int create(PageDto dto, List<String> pageLabels) {
        requireCanCreate(dto.getParent());
        var user = users.getUserContext();
        Integer actor = user.isGuest() ? null : Integer.valueOf(user.getUserId());
        LocalDateTime now = LocalDateTime.now();
        dto.setId(null);
        dto.setParent(dto.getParent() == null ? 0 : dto.getParent());
        dto.setCreatedBy(actor);
        dto.setChangedBy(actor);
        dto.setCreateDate(now);
        dto.setChangeDate(now);
        dto.setActive(true);
        dto.setDeleted(false);
        int id = pages.createPage(new PageDtoConverter().convertBtoA(dto));
        permissions.createDefaultPermissions(new PageId(id), actor);
        labels.setForPage(new PageId(id), pageLabels);
        return id;
    }
}
