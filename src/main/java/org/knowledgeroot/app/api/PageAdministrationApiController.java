package org.knowledgeroot.app.api;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.*;
import org.knowledgeroot.app.util.RequestValidation;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/** Version history and page sharing. */
@RestController
@RequestMapping("/api/v1/pages/{id}")
@RequiredArgsConstructor
class PageAdministrationApiController {
    private static final int HISTORY_PAGE_SIZE = 50;
    private final ApiSupport api;
    private final PageDao pages;
    private final PageEditingService editing;
    private final PageLabelDao labels;
    private final PagePermissionDao permissions;
    private final org.knowledgeroot.app.security.user.domain.UserDao users;
    private final org.knowledgeroot.app.security.user.domain.GroupDao groups;

    @GetMapping("/history")
    ApiModels.HistoryResponse history(@PathVariable int id, @RequestParam(defaultValue = "0") int start) {
        var pageId = new PageId(id);
        var page = editing.historyPage(pageId);
        start = RequestValidation.start(start);
        var items = pages.listRevisions(pageId, start);
        return new ApiModels.HistoryResponse(ApiModels.HistoryPage.of(page),
                items.stream().limit(HISTORY_PAGE_SIZE).map(ApiModels.RevisionSummary::of).toList(),
                start, items.size() > HISTORY_PAGE_SIZE);
    }

    @GetMapping("/history/{historyId}")
    ApiModels.RevisionDetail revision(@PathVariable int id, @PathVariable int historyId) {
        var page = editing.historyPage(new PageId(id));
        var previous = pages.findRevision(page.getPageId(), historyId);
        return new ApiModels.RevisionDetail(previous.id(), previous.revision(), previous.name(), previous.content(),
                previous.changedAt(), previous.labelsCaptured(), previous.labels(), ApiModels.HistoryPage.of(page));
    }

    /** Compares saved version {@code from} with saved version {@code to}, or with the current page when to=0. */
    @GetMapping("/history/compare")
    @Transactional(readOnly = true)
    ApiModels.CompareResponse compare(@PathVariable int id, @RequestParam int from, @RequestParam(defaultValue = "0") int to) {
        RequestValidation.require(from > 0 && to >= 0);
        var page = editing.historyPage(new PageId(id));
        var left = pages.findRevision(page.getPageId(), from);
        var right = to == 0 ? new PageRevision(0, page.getRevision(), page.getName(), page.getContent(),
                page.getChangeDate(), true, labels.listForPage(page.getPageId())) : pages.findRevision(page.getPageId(), to);
        return new ApiModels.CompareResponse(ApiModels.HistoryPage.of(page), side(left, false), side(right, to == 0),
                ApiModels.DiffPart.of(RevisionDiff.compare(left.name(), right.name())),
                ApiModels.DiffPart.of(RevisionDiff.compare(RevisionDiff.text(left.content()), RevisionDiff.text(right.content()))),
                ApiModels.DiffPart.of(RevisionDiff.compare(left.content(), right.content())));
    }

    /** Restores name, content and captured labels; also undeletes the page. */
    @PostMapping("/history/{historyId}/restore")
    ApiModels.HistoryPage restore(@PathVariable int id, @PathVariable int historyId,
                                  @RequestBody ApiModels.RevisionInput input) {
        editing.restore(new PageId(id), historyId, input == null ? null : input.revision());
        return ApiModels.HistoryPage.of(pages.findById(new PageId(id)));
    }

    @GetMapping("/permissions")
    ApiModels.PermissionInfo permissions(@PathVariable int id) {
        api.requireAdmin();
        return permissionInfo(id);
    }

    @PutMapping("/permissions/mode")
    ApiModels.PermissionInfo mode(@PathVariable int id, @RequestBody ApiModels.PermissionModeInput input) {
        api.requireAdmin();
        try {
            permissions.setInheriting(new PageId(id), input.inherit(), api.viewer());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Top-level pages cannot inherit permissions.");
        }
        return permissionInfo(id);
    }

    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    ApiModels.PermissionInfo addPermission(@PathVariable int id, @RequestBody ApiModels.PermissionInput input) {
        PageId pageId = requireLocalPermissions(id);
        PagePermission.RoleType role;
        PagePermission.PermissionLevel level;
        try {
            role = PagePermission.RoleType.fromString(input.roleType());
            level = PagePermission.PermissionLevel.fromString(input.level());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a role and a permission level.");
        }
        Integer roleId = role == PagePermission.RoleType.GUEST ? null : input.roleId();
        if (role != PagePermission.RoleType.GUEST && (roleId == null || roleId <= 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a user or group.");
        if (role == PagePermission.RoleType.USER
                && users.findById(new org.knowledgeroot.app.security.user.domain.UserId(roleId)) == null
                || role == PagePermission.RoleType.GROUP
                && groups.findById(new org.knowledgeroot.app.security.user.domain.GroupId(roleId)) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected user or group does not exist.");
        boolean duplicate = permissions.listPermissionsByPageId(pageId).stream().anyMatch(existing ->
                existing.getRoleType() == role && java.util.Objects.equals(existing.getRoleId(), roleId));
        if (duplicate) throw new ResponseStatusException(HttpStatus.CONFLICT, "This role already has a permission on the page.");
        LocalDateTime now = LocalDateTime.now();
        permissions.createPermission(PagePermission.builder().pageId(pageId).roleType(role).roleId(roleId)
                .permissionLevel(level).createdBy(api.viewer()).createDate(now).changedBy(api.viewer()).changeDate(now)
                .build());
        return permissionInfo(id);
    }

    @PutMapping("/permissions/{permissionId}")
    ApiModels.PermissionInfo updatePermission(@PathVariable int id, @PathVariable int permissionId,
                                              @RequestBody ApiModels.PermissionInput input) {
        PageId pageId = requireLocalPermissions(id);
        PagePermission.PermissionLevel level;
        try {
            level = PagePermission.PermissionLevel.fromString(input.level());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a permission level.");
        }
        permissions.updatePermissionForPage(pageId, PagePermission.builder().id(permissionId).permissionLevel(level)
                .changedBy(api.viewer()).changeDate(LocalDateTime.now()).build());
        return permissionInfo(id);
    }

    @DeleteMapping("/permissions/{permissionId}")
    ApiModels.PermissionInfo deletePermission(@PathVariable int id, @PathVariable int permissionId) {
        permissions.deletePermissionForPage(requireLocalPermissions(id), permissionId);
        return permissionInfo(id);
    }

    private PageId requireLocalPermissions(int id) {
        api.requireAdmin();
        PageId pageId = new PageId(id);
        if (permissions.isInheriting(pageId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Switch to local permissions first.");
        return pageId;
    }

    private ApiModels.PermissionInfo permissionInfo(int id) {
        PageId pageId = new PageId(id);
        Page page = api.page(id);
        boolean inherits = permissions.isInheriting(pageId);
        PageId source = permissions.permissionSource(pageId);
        ApiModels.PageRef sourceRef = source == null ? null : ApiModels.PageRef.of(pages.findById(source));
        var entries = inherits ? java.util.List.<ApiModels.PermissionEntry>of()
                : permissions.listPermissionsByPageId(pageId).stream().map(this::entry).toList();
        return new ApiModels.PermissionInfo(id, inherits, sourceRef, page.getParent() != null && page.getParent() > 0, entries);
    }

    /** Users without first and last name are shown by login instead of a blank name. */
    private ApiModels.PermissionEntry entry(PagePermission permission) {
        var entry = ApiModels.PermissionEntry.of(permission);
        if (entry.roleName() != null && !entry.roleName().isBlank()) return entry;
        String name = null;
        if (permission.getRoleType() == PagePermission.RoleType.USER && permission.getRoleId() != null) {
            var user = users.findById(new org.knowledgeroot.app.security.user.domain.UserId(permission.getRoleId()));
            if (user != null) name = user.getLogin();
        }
        return new ApiModels.PermissionEntry(entry.id(), entry.roleType(), entry.roleId(),
                name != null ? name : entry.roleType() + " #" + entry.roleId(), entry.level());
    }

    private static ApiModels.CompareSide side(PageRevision revision, boolean current) {
        return new ApiModels.CompareSide(revision.id(), revision.revision(), revision.name(), revision.changedAt(), current);
    }
}
