package org.knowledgeroot.app.api;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.page.domain.*;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reading, writing, starring and moving pages. Permission rules match the web UI. */
@RestController
@RequestMapping("/api/v1/pages")
@RequiredArgsConstructor
class PageApiController {
    private final ApiSupport api;
    private final PageDao pages;
    private final PagePermissionDao permissions;
    private final PageLabelDao labels;
    private final PageStarDao stars;
    private final PageCommentDao comments;
    private final FileDao files;
    private final PageCreationService creation;
    private final PageEditingService editing;
    private final PageMoveService moves;
    private final PageDraftDao drafts;

    @GetMapping("/{id}")
    ApiModels.PageDetail page(@PathVariable int id) {
        api.require(id, PagePermission.PermissionLevel.VIEW);
        return detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiModels.Created create(@RequestBody ApiModels.PageInput input) {
        var dto = PageDto.builder().parent(input.parent() == null ? 0 : input.parent()).spaceId(input.space())
                .name(trim(input.name())).content(input.content()).published(input.published()).build();
        return new ApiModels.Created(creation.create(dto, labels(input.labels())));
    }

    /**
     * Publishes name, content and labels as a new version and drops the working copy; {@code revision}
     * must match the edited version (409 otherwise). {@code published: true} also publishes a draft page.
     */
    @PutMapping("/{id}")
    ApiModels.PageDetail update(@PathVariable int id, @RequestBody ApiModels.PageInput input) {
        var dto = PageDto.builder().id(id).name(trim(input.name())).content(input.content())
                .revision(input.revision()).published(input.published()).build();
        editing.edit(new PageId(id), dto, labels(input.labels()), java.util.Map.of(), List.of(), List.of());
        return detail(id);
    }

    /** The working copy (R62) for users who may edit the page; 404 when there is none. */
    @GetMapping("/{id}/draft")
    ApiModels.WorkingCopy draft(@PathVariable int id) {
        api.require(id, PagePermission.PermissionLevel.EDIT);
        return editing.draft(new PageId(id)).map(copy -> workingCopy(id, copy))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** Autosave: stores name, content and labels as the working copy; {@code revision} is the version the editor opened. */
    @PutMapping("/{id}/draft")
    ApiModels.WorkingCopy saveDraft(@PathVariable int id, @RequestBody ApiModels.PageInput input) {
        var copy = editing.saveDraft(new PageId(id), trim(input.name()), input.content(), labels(input.labels()), input.revision());
        return workingCopy(id, copy);
    }

    @DeleteMapping("/{id}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discardDraft(@PathVariable int id) {
        editing.discardDraft(new PageId(id));
    }

    /** Publishes the working copy as a new version, or a draft page as it is. */
    @PostMapping("/{id}/publish")
    ApiModels.PageDetail publish(@PathVariable int id, @RequestBody ApiModels.PublishInput input) {
        editing.publish(new PageId(id), input.revision());
        return detail(id);
    }

    private ApiModels.WorkingCopy workingCopy(int id, PageDraft copy) {
        long current = pages.findById(new PageId(id)).getRevision();
        return new ApiModels.WorkingCopy(id, copy.name(), copy.content(), copy.labels(), copy.baseRevision(), copy.isStale(current),
                copy.changedBy(), copy.changedByLogin(), copy.changeDate());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable int id, @RequestParam(required = false) Long revision) {
        api.require(id, PagePermission.PermissionLevel.EDIT);
        editing.delete(new PageId(id), revision);
    }

    @PutMapping("/{id}/star")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void star(@PathVariable int id) {
        int user = api.requireUser();
        api.require(id, PagePermission.PermissionLevel.VIEW);
        stars.starPage(user, new PageId(id));
    }

    @DeleteMapping("/{id}/star")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unstar(@PathVariable int id) {
        stars.unstarPage(api.requireUser(), new PageId(id));
    }

    /**
     * Destination browser for moving a page: readable pages below {@code parent} (or matching {@code query}).
     * Only editable destinations outside the page's own subtree are valid.
     */
    @GetMapping("/{id}/move-options")
    ApiModels.MoveOptions moveOptions(@PathVariable int id, @RequestParam(required = false) Integer parent,
                                      @RequestParam(defaultValue = "0") int start,
                                      @RequestParam(defaultValue = "") String query,
                                      @RequestParam(required = false) Integer space) {
        var page = moves.movablePage(new PageId(id));
        int currentParent = page.getParent() == null ? 0 : page.getParent();
        if (parent == null) parent = currentParent > 0 && api.can(currentParent, PagePermission.PermissionLevel.VIEW)
                ? currentParent : 0;
        RequestValidation.require(parent >= 0 && (space == null || space > 0));
        if (parent > 0) api.require(parent, PagePermission.PermissionLevel.VIEW);
        // The top level is browsed per space: the page's own unless another one was chosen.
        int destinationSpace = parent > 0 ? pages.findById(new PageId(parent)).getSpaceId()
                : space != null ? space : page.getSpaceId();
        if (!api.spaces().canView(destinationSpace)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this space.");
        RequestValidation.text(query, 255);
        start = RequestValidation.start(start);
        var filter = PageFilter.builder().parent(query.isBlank() ? parent : null).query(query.isBlank() ? null : query)
                .spaceId(query.isBlank() && parent == 0 ? destinationSpace : null)
                .deleted(false).start(start).limit(51).build();
        var items = pages.listVisiblePages(filter, moves.actor());
        var destinationPath = api.path(parent);
        boolean insideSubtree = destinationPath.stream().anyMatch(ancestor -> ancestor.id() == id);
        boolean sameSpace = destinationSpace == page.getSpaceId();
        boolean valid = parent == 0 ? (sameSpace || moves.canUseTopLevel(destinationSpace))
                : moves.canUseDestination(parent) && !insideSubtree && pages.getPageHierarchy(
                new PageId(parent)).stream().noneMatch(ancestor -> ancestor.getPageId().value() == id);
        return new ApiModels.MoveOptions(ApiModels.PageRef.of(page), currentParent, api.path(currentParent), parent,
                api.spaceRef(destinationSpace), destinationPath, items.stream().limit(50).filter(item -> item.getPageId().value() != id)
                .map(item -> new ApiModels.MoveCandidate(item.getPageId().value(), item.getName())).toList(),
                start, items.size() > 50, parent == currentParent && (parent > 0 || sameSpace), valid, permissions.isInheriting(page.getPageId()));
    }

    @PostMapping("/{id}/move")
    ApiModels.PageDetail move(@PathVariable int id, @RequestBody ApiModels.MoveInput input) {
        moves.move(new PageId(id), input.parent(), input.revision(), input.space());
        return detail(id);
    }

    private ApiModels.PageDetail detail(int id) {
        PageId pid = new PageId(id);
        Integer viewer = api.viewer();
        Page page = api.page(id);
        boolean canEdit = api.can(id, PagePermission.PermissionLevel.EDIT);
        var breadcrumb = new ArrayList<>(api.path(id));
        if (!breadcrumb.isEmpty() && breadcrumb.getLast().id() == id) breadcrumb.removeLast();
        // Reading a child does not reveal a hidden parent's ID.
        Integer parent = page.getParent() != null && page.getParent() > 0
                && !api.can(page.getParent(), PagePermission.PermissionLevel.VIEW) ? null : page.getParent();
        var attachments = files.listFiles(FileFilter.builder().pageId(pid).build()).stream()
                .map(ApiModels.FileInfo::of).toList();
        return new ApiModels.PageDetail(id, page.getRevision(), api.spaceRef(page.getSpaceId()), parent, page.getName(), page.getContent(),
                labels.listForPage(pid), breadcrumb, Boolean.TRUE.equals(page.getDeleted()),
                page.getCreatedBy(), page.getCreatedByLogin(), page.getCreateDate(),
                page.getChangedBy(), page.getChangedByLogin(), page.getChangeDate(),
                canEdit, viewer != null, viewer != null && stars.isStarred(viewer, pid), viewer != null,
                api.admin(), attachments, comments.countForPage(pid),
                page.isPublished(), canEdit && drafts.find(pid).isPresent());
    }

    private static List<String> labels(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(label -> !label.isEmpty()).toList();
    }

    private static String trim(String value) {
        if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a page name.");
        return value.trim();
    }
}
