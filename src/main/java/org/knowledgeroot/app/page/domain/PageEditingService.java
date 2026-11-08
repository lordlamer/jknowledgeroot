package org.knowledgeroot.app.page.domain;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.page.api.PageDtoConverter;
import org.knowledgeroot.app.sanitizer.Sanitizer;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PageEditingService {
    private final PageDao pages;
    private final PagePermissionDao permissions;
    private final PageLabelDao labels;
    private final UserContext users;
    private final PageDraftDao drafts;
    private final org.springframework.context.ApplicationEventPublisher events;

    /**
     * Publishes the submitted name, content and labels as a new version. The page's working copy
     * is dropped because the editor sends its full state. A draft page becomes visible to readers
     * when {@code dto.published} is true; a published page never becomes a draft again.
     */
    @Transactional
    public void edit(PageId id, PageDto dto, List<String> pageLabels,
                     Map<String, String> updates, List<String> deletions, List<Map<String, String>> additions) {
        edit(id, dto, pageLabels, updates, deletions, additions, false);
    }

    /**
     * {@code live} says the content is the shared live document of the page (R63): the working copy
     * is dropped, but the live log stays and open editors learn the new revision instead of being
     * closed. Content from anywhere else (API, MCP, an editor without live session) resets the log.
     */
    @Transactional
    public void edit(PageId id, PageDto dto, List<String> pageLabels,
                     Map<String, String> updates, List<String> deletions, List<Map<String, String>> additions,
                     boolean live) {
        Integer actor = actor();
        Page page = editablePage(id, actor);
        PageInput.validate(dto);
        PageInput.labels(pageLabels);
        requireRevision(page, dto.getRevision());
        if (!updates.isEmpty() || !deletions.isEmpty() || !additions.isEmpty()) {
            if (!users.getUserContext().isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            if (permissions.isInheriting(id)) throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        setContent(page, dto, actor, now);
        if (Boolean.TRUE.equals(dto.getPublished())) page.setPublished(true);
        pages.updatePage(page);
        labels.setForPage(id, pageLabels);
        if (live) published(id); else clearWorkingCopy(id);
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
        requireRevision(page, dto.getRevision());
        setContent(page, dto, actor, LocalDateTime.now());
        page.setTimeStart(dto.getTimeStart());
        page.setTimeEnd(dto.getTimeEnd());
        if (dto.getActive() != null) page.setActive(dto.getActive());
        if (dto.getDeleted() != null && Boolean.TRUE.equals(dto.getDeleted()) != Boolean.TRUE.equals(page.getDeleted()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use delete or history restore");
        if (Boolean.TRUE.equals(dto.getPublished())) page.setPublished(true);
        pages.updatePage(page);
        clearWorkingCopy(id);
        // Return persisted audit identity and creation fields, never the client-supplied values.
        return new PageDtoConverter().convertAtoB(page);
    }

    /**
     * Autosave (R62): stores the editor's state as the page's working copy without a new version.
     * {@code revision} is the page revision the editor was opened with; the first save fixes it as
     * the base revision, later saves keep it so that a page published meanwhile is detectable.
     */
    @Transactional
    public PageDraft saveDraft(PageId id, String name, String content, List<String> pageLabels, Long revision) {
        return saveDraft(id, name, content, pageLabels, revision, false);
    }

    /**
     * {@code live}: the browser is in a live session (R63). Its revision then has to be the current
     * one; an older one means the page was just published from the live document and the browser
     * has not received the new revision yet, so it saves again a moment later.
     */
    @Transactional
    public PageDraft saveDraft(PageId id, String name, String content, List<String> pageLabels, Long revision, boolean live) {
        Integer actor = actor();
        Page page = editablePage(id, actor);
        var dto = PageDto.builder().name(name).content(content).build();
        PageInput.validate(dto);
        PageInput.labels(pageLabels);
        if (revision == null || revision < 0) throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "A revision is required");
        if (revision > page.getRevision()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Page has changed");
        if (live && revision < page.getRevision()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Page has changed");
        long base = drafts.find(id).map(PageDraft::baseRevision).orElse(revision);
        drafts.save(new PageDraft(id, dto.getName(), Sanitizer.sanitize(dto.getContent()),
                pageLabels == null ? List.of() : pageLabels, base, actor, null, LocalDateTime.now()));
        return drafts.find(id).orElseThrow();
    }

    /** The working copy, for users who may edit the page; empty when there is none. */
    public Optional<PageDraft> draft(PageId id) {
        editablePage(id, actor());
        return drafts.find(id);
    }

    @Transactional
    public void discardDraft(PageId id) {
        editablePage(id, actor());
        clearWorkingCopy(id);
    }

    /**
     * Publishes the working copy as a new version, or makes a draft page without a working copy
     * visible as it is. {@code revision} must match the page's current revision.
     */
    @Transactional
    public void publish(PageId id, Long revision) {
        Integer actor = actor();
        Page page = editablePage(id, actor);
        requireRevision(page, revision);
        var draft = drafts.find(id);
        if (draft.isEmpty() && page.isPublished()) throw new ResponseStatusException(HttpStatus.CONFLICT, "There is nothing to publish");
        draft.ifPresent(copy -> {
            page.setName(copy.name());
            page.setContent(copy.content());
        });
        page.setPublished(true);
        page.setChangedBy(actor);
        page.setChangeDate(LocalDateTime.now());
        pages.updatePage(page);
        draft.ifPresent(copy -> labels.setForPage(id, copy.labels()));
        // The working copy is the saved state of the live document, so the live log stays valid.
        published(id);
    }

    /** Drops the working copy and its live-collaboration log and tells open editors (R63). */
    private void clearWorkingCopy(PageId id) {
        drafts.delete(id);
        events.publishEvent(new WorkingCopyCleared(id));
    }

    /** Drops the working copy after a publish from the live document; open editors get the new revision (R63). */
    private void published(PageId id) {
        drafts.deleteKeepingLog(id);
        events.publishEvent(new WorkingCopyPublished(id, pages.findById(id).getRevision(), users.getUserContext().getLogin()));
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

    public static void requireRevision(Page page, Long expected) {
        if (expected == null || expected < 0) throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "A revision is required");
        if (!expected.equals(page.getRevision())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Page has changed");
    }

    @Transactional
    public void delete(PageId id, Long revision) {
        Integer actor = actor();
        Page page = editablePage(id, actor);
        requireRevision(page, revision);
        page.setDeleted(true);
        page.setChangedBy(actor);
        page.setChangeDate(LocalDateTime.now());
        pages.updatePage(page);
        clearWorkingCopy(id);
    }

    public Page historyPage(PageId id) {
        if (users.getUserContext().isAdmin()) return pages.findById(id);
        return editablePage(id, actor());
    }

    @Transactional
    public void restore(PageId id, int historyId, Long revision) {
        Page page = historyPage(id);
        requireRevision(page, revision);
        var previous = pages.findRevision(id, historyId);
        if (previous.labelsCaptured()) PageInput.labels(previous.labels());
        page.setName(previous.name());
        page.setContent(previous.content()); // Re-sanitize historical content with the current policy.
        page.setDeleted(false);
        page.setChangedBy(actor());
        page.setChangeDate(LocalDateTime.now());
        pages.updatePage(page);
        if (previous.labelsCaptured()) labels.setForPage(id, previous.labels());
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
