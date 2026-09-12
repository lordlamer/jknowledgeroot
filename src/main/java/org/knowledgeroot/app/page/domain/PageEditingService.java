package org.knowledgeroot.app.page.domain;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.page.api.PageDtoConverter;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PageEditingService {
    private final PageDao pages;
    private final PagePermissionDao permissions;
    private final PageLabelDao labels;
    private final UserContext users;

    @Transactional
    public void edit(PageId id, PageDto dto, List<String> pageLabels,
                     Map<String, String> updates, List<String> deletions, List<Map<String, String>> additions) {
        Integer actor = actor();
        Page page = editablePage(id, actor);
        PageInput.validate(dto);
        PageInput.labels(pageLabels);
        if (!updates.isEmpty() || !deletions.isEmpty() || !additions.isEmpty()) {
            if (!users.getUserContext().isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            if (permissions.isInheriting(id)) throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        setContent(page, dto, actor, now);
        pages.updatePage(page);
        labels.setForPage(id, pageLabels);
        try {
            for (var entry : updates.entrySet()) {
                permissions.updatePermissionForPage(id, PagePermission.builder()
                        .id(positiveId(entry.getKey()))
                        .permissionLevel(PagePermission.PermissionLevel.fromString(entry.getValue()))
                        .changedBy(actor).changeDate(now).build());
            }
            for (String deletion : deletions) permissions.deletePermissionForPage(id, positiveId(deletion));
            for (var addition : additions) {
                var role = PagePermission.RoleType.fromString(addition.get("roleType"));
                Integer roleId = role == PagePermission.RoleType.GUEST ? null : positiveId(addition.get("roleId"));
                permissions.createPermission(PagePermission.builder().pageId(id).roleType(role).roleId(roleId)
                        .permissionLevel(PagePermission.PermissionLevel.fromString(addition.get("permissionLevel")))
                        .createdBy(actor).changedBy(actor).createDate(now).changeDate(now).build());
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid permission change");
        }
    }

    @Transactional
    public PageDto update(PageId id, PageDto dto) {
        Integer actor = actor();
        Page page = editablePage(id, actor);
        PageInput.validate(dto);
        setContent(page, dto, actor, LocalDateTime.now());
        page.setTimeStart(dto.getTimeStart());
        page.setTimeEnd(dto.getTimeEnd());
        if (dto.getActive() != null) page.setActive(dto.getActive());
        if (dto.getDeleted() != null) page.setDeleted(dto.getDeleted());
        pages.updatePage(page);
        // Return persisted audit identity and creation fields, never the client-supplied values.
        return new PageDtoConverter().convertAtoB(page);
    }

    private Page editablePage(PageId id, Integer actor) {
        if (!permissions.hasUserPermission(id, actor, PagePermission.PermissionLevel.EDIT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        try {
            Page page = pages.findById(id);
            if (page == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return page;
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private Integer actor() {
        var user = users.getUserContext();
        return user.isGuest() ? null : Integer.valueOf(user.getUserId());
    }

    private void setContent(Page page, PageDto dto, Integer actor, LocalDateTime now) {
        page.setName(dto.getName());
        page.setContent(dto.getContent());
        page.setChangedBy(actor);
        page.setChangeDate(now);
    }

    private int positiveId(String value) {
        if (value == null) throw new IllegalArgumentException();
        int id = Integer.parseInt(value.trim());
        if (id <= 0) throw new IllegalArgumentException();
        return id;
    }
}
