package org.knowledgeroot.app.mcp;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageCommentDao;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PageLabelDao;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.space.SpaceService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Page access shared by the MCP tools, resources and prompts: visibility, links, paths, details. */
@Component
@RequiredArgsConstructor
class McpPages {
    static final String NOT_READABLE = "The page was not found or you may not read it.";
    private final McpUrls urls;
    private final SpaceService spaces;
    private final PageDao pages;
    private final PagePermissionDao permissions;
    private final PageLabelDao labels;
    private final PageCommentDao comments;
    private final FileDao files;

    boolean canView(int pageId) {
        return pageId > 0 && permissions.hasUserPermission(new PageId(pageId), McpToolSupport.userId(), PagePermission.PermissionLevel.VIEW);
    }

    boolean canEdit(int pageId) {
        return pageId > 0 && permissions.hasUserPermission(new PageId(pageId), McpToolSupport.userId(), PagePermission.PermissionLevel.EDIT);
    }

    /** The page when the user may read it; hidden and deleted pages look the same as missing ones. */
    Page readable(int pageId) {
        if (!canView(pageId)) throw new McpToolException(NOT_READABLE);
        try {
            Page page = pages.findById(new PageId(pageId));
            if (page == null || Boolean.TRUE.equals(page.getDeleted())) throw new McpToolException(NOT_READABLE);
            return page;
        } catch (EmptyResultDataAccessException e) {
            throw new McpToolException(NOT_READABLE);
        }
    }

    Page editable(int pageId) {
        Page page = readable(pageId);
        if (!canEdit(pageId)) throw new McpToolException("You may not edit this page.");
        return page;
    }

    int requireSpace(Integer spaceId) {
        int id = spaceId == null ? spaces.defaultSpace().getId() : spaceId;
        if (spaces.spaces().find(id).isEmpty() || !spaces.canView(id))
            throw new McpToolException("The space was not found or you may not enter it.");
        return id;
    }

    McpModels.SpaceRef spaceRef(Integer spaceId) {
        if (spaceId == null) return null;
        return spaces.spaces().find(spaceId).map(space -> new McpModels.SpaceRef(space.getId(), space.getKey(), space.getName())).orElse(null);
    }

    String origin() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? urls.origin(attributes.getRequest()) : urls.configured().orElse("");
    }

    String pageUrl(int id) {
        return origin() + "/ui/page/" + id;
    }

    /** Root-to-page path limited to pages the user may see. */
    List<McpModels.PageRef> path(Integer pageId) {
        if (pageId == null || pageId <= 0) return List.of();
        return pages.getPageHierarchy(new PageId(pageId)).stream()
                .filter(ancestor -> canView(ancestor.getPageId().value())).map(McpModels.PageRef::of).toList();
    }

    List<McpModels.PageRef> breadcrumb(int id) {
        var breadcrumb = new ArrayList<>(path(id));
        if (!breadcrumb.isEmpty() && breadcrumb.getLast().id() == id) breadcrumb.removeLast();
        return breadcrumb;
    }

    McpModels.PageDetail detail(Page page, String format) {
        int id = page.getPageId().value();
        return new McpModels.PageDetail(id, page.getName(), page.getRevision(), pageUrl(id), spaceRef(page.getSpaceId()),
                breadcrumb(id), labels.listForPage(page.getPageId()), format, McpMarkdown.render(page.getContent(), format),
                page.getChangedByLogin(), McpModels.time(page.getChangeDate()), canEdit(id), comments.countForPage(page.getPageId()),
                files.listFiles(FileFilter.builder().pageId(page.getPageId()).build()).size());
    }

    static String trimName(String name) {
        if (name == null || name.isBlank()) throw new McpToolException("Enter a page name.");
        if (name.trim().length() > 255) throw new McpToolException("Page names are limited to 255 characters.");
        return name.trim();
    }

    static List<String> labels(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > 32) throw new McpToolException("A page takes at most 32 labels.");
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(label -> !label.isEmpty()).toList();
    }

    static int offset(Integer start) {
        return start == null || start < 0 ? 0 : Math.min(start, 100_000);
    }
}
