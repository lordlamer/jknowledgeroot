package org.knowledgeroot.app.mcp;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.frontend.web.SearchController;
import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageCommentDao;
import org.knowledgeroot.app.page.domain.PageCreationService;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageEditingService;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PageLabelDao;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.page.domain.PageStarDao;
import org.knowledgeroot.app.space.SpaceService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The tools an AI assistant gets over {@code /mcp}. Every tool runs as the user behind the
 * access token with the normal space and page permissions; changing tools additionally need the
 * {@code mcp:write} scope the user granted on the consent page.
 */
@Component
@RequiredArgsConstructor
public class McpTools {
    private static final int PAGE_SIZE = 20;
    private static final int TREE_SIZE = 50;
    private static final int MAX_COMMENT_LENGTH = 4000;
    private final McpToolSupport support;
    private final McpUrls urls;
    private final SpaceService spaces;
    private final PageDao pages;
    private final PagePermissionDao permissions;
    private final PageLabelDao labels;
    private final PageStarDao stars;
    private final PageCommentDao comments;
    private final PageCreationService creation;
    private final PageEditingService editing;

    // --- reading ------------------------------------------------------------------------------------

    @McpTool(name = "list_spaces", generateOutputSchema = false,
            description = "Lists the spaces (separate page trees) the user may enter, with id, key, name and description. "
                    + "Pass a space id to search_pages or list_pages to stay inside one space.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<McpModels.Space> listSpaces() {
        return support.read("list_spaces", () -> spaces.visibleSpaces().stream().map(McpModels.Space::of).toList());
    }

    @McpTool(name = "search_pages", generateOutputSchema = false,
            description = "Full-text search over page names and contents the user may read. Returns up to 20 hits with id, "
                    + "name, space, path and a snippet; use start for the next 20. Call get_page for the full content.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.SearchResult searchPages(
            @McpToolParam(description = "Words to search for", required = true) String query,
            @McpToolParam(description = "Only search this space (id from list_spaces)", required = false) Integer space,
            @McpToolParam(description = "Offset of the first hit (0, 20, 40, ...)", required = false) Integer start) {
        return support.read("search_pages", () -> {
            String term = query == null ? "" : query.trim();
            if (term.isEmpty()) throw new McpToolException("Pass a search term.");
            if (term.length() > 200) throw new McpToolException("The search term is limited to 200 characters.");
            int offset = start == null || start < 0 ? 0 : Math.min(start, 100_000);
            if (space != null) requireSpace(space);
            Integer viewer = McpToolSupport.userId();
            List<Page> results = pages.listVisiblePages(PageFilter.builder().query(term).spaceId(space).start(offset)
                    .limit(PAGE_SIZE + 1).build(), viewer);
            var hits = results.stream().limit(PAGE_SIZE).map(page -> new McpModels.SearchHit(page.getPageId().value(),
                    page.getName(), pageUrl(page.getPageId().value()), spaceRef(page.getSpaceId()),
                    path(page.getParent()).stream().map(McpModels.PageRef::name).toList(),
                    SearchController.snippet(page.getContent(), term), McpModels.time(page.getChangeDate()))).toList();
            return new McpModels.SearchResult(term, hits, offset, results.size() > PAGE_SIZE);
        });
    }

    @McpTool(name = "get_page", generateOutputSchema = false,
            description = "Reads one page: name, space, breadcrumb, labels, the content as HTML, the revision needed for "
                    + "update_page and a link for the user.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.PageDetail getPage(@McpToolParam(description = "Page id", required = true) int id) {
        return support.read("get_page", () -> detail(readable(id)));
    }

    @McpTool(name = "list_pages", generateOutputSchema = false,
            description = "Lists the child pages of a page, or the top-level pages of a space when parent is omitted "
                    + "(space defaults to the default space). Up to 50 entries; hasChildren says where to descend.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.PageList listPages(
            @McpToolParam(description = "Parent page id; omit for the top level of a space", required = false) Integer parent,
            @McpToolParam(description = "Space id for the top level (from list_spaces)", required = false) Integer space) {
        return support.read("list_pages", () -> {
            Integer viewer = McpToolSupport.userId();
            McpModels.PageRef parentRef = null;
            int spaceId;
            if (parent != null && parent > 0) {
                Page parentPage = readable(parent);
                parentRef = McpModels.PageRef.of(parentPage);
                spaceId = parentPage.getSpaceId() == null ? spaces.defaultSpace().getId() : parentPage.getSpaceId();
            } else {
                spaceId = space == null ? spaces.defaultSpace().getId() : space;
                requireSpace(spaceId);
            }
            List<Page> visible = pages.listNavigationPages(spaceId, parentRef == null ? 0 : parentRef.id(), 0, viewer);
            List<Page> items = visible.stream().limit(TREE_SIZE).toList();
            Set<Integer> withChildren = pages.parentsWithVisibleChildren(
                    items.stream().map(page -> page.getPageId().value()).toList(), viewer);
            return new McpModels.PageList(parentRef, spaceRef(spaceId), items.stream().map(page -> new McpModels.PageNode(
                    page.getPageId().value(), page.getName(), withChildren.contains(page.getPageId().value()),
                    pageUrl(page.getPageId().value()))).toList(), visible.size() > TREE_SIZE);
        });
    }

    @McpTool(name = "list_starred", generateOutputSchema = false,
            description = "The pages the user starred as favourites, most recently starred first.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<McpModels.PageNode> listStarred() {
        return support.read("list_starred", () -> stars.listStarredPages(McpToolSupport.userId()).stream()
                .filter(page -> canView(page.getPageId().value()))
                .map(page -> new McpModels.PageNode(page.getPageId().value(), page.getName(), false, pageUrl(page.getPageId().value())))
                .toList());
    }

    @McpTool(name = "list_comments", generateOutputSchema = false,
            description = "The comments on a page, oldest first.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<McpModels.Comment> listComments(@McpToolParam(description = "Page id", required = true) int page) {
        return support.read("list_comments", () -> {
            readable(page);
            return comments.listForPage(new PageId(page)).stream().map(comment -> new McpModels.Comment(comment.getId(),
                    comment.getAuthorDisplayName(), McpModels.time(comment.getCreateDate()), comment.getContent())).toList();
        });
    }

    // --- writing (scope mcp:write) -------------------------------------------------------------------

    @McpTool(name = "create_page", generateOutputSchema = false,
            description = "Creates a page below a parent page, or as a top-level page of a space when parent is omitted. "
                    + "Content is HTML (paragraphs, headings, lists, links, tables); scripts and styles are removed.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public McpModels.Saved createPage(
            @McpToolParam(description = "Page name (up to 255 characters)", required = true) String name,
            @McpToolParam(description = "Content as HTML", required = true) String content,
            @McpToolParam(description = "Parent page id; omit for a top-level page", required = false) Integer parent,
            @McpToolParam(description = "Space id for a top-level page; defaults to the default space", required = false) Integer space,
            @McpToolParam(description = "Labels (tags) for the page", required = false) List<String> labels) {
        return support.write("create_page", () -> {
            var dto = PageDto.builder().parent(parent == null ? 0 : parent).spaceId(parent == null ? space : null)
                    .name(trim(name)).content(content == null ? "" : content).build();
            int id = creation.create(dto, labels(labels));
            Page created = pages.findById(new PageId(id));
            return new McpModels.Saved(id, created.getName(), created.getRevision(), pageUrl(id), "Page created.");
        });
    }

    @McpTool(name = "update_page", generateOutputSchema = false,
            description = "Saves a new name, content (HTML) or labels of a page. Pass the revision from get_page; the save "
                    + "is refused when the page changed meanwhile. Omitted fields keep their current value.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public McpModels.Saved updatePage(
            @McpToolParam(description = "Page id", required = true) int id,
            @McpToolParam(description = "Revision from get_page", required = true) long revision,
            @McpToolParam(description = "New page name", required = false) String name,
            @McpToolParam(description = "New content as HTML", required = false) String content,
            @McpToolParam(description = "New labels; replaces the current ones", required = false) List<String> labels) {
        return support.write("update_page", () -> {
            Page current = readable(id);
            if (!permissions.hasUserPermission(new PageId(id), McpToolSupport.userId(), PagePermission.PermissionLevel.EDIT))
                throw new McpToolException("You may not edit this page.");
            var dto = PageDto.builder().id(id).name(name == null ? current.getName() : trim(name))
                    .content(content == null ? current.getContent() : content).revision(revision).build();
            List<String> newLabels = labels == null ? this.labels.listForPage(new PageId(id)) : labels(labels);
            editing.edit(new PageId(id), dto, newLabels, java.util.Map.of(), List.of(), List.of());
            Page saved = pages.findById(new PageId(id));
            return new McpModels.Saved(id, saved.getName(), saved.getRevision(), pageUrl(id), "Page saved.");
        });
    }

    @McpTool(name = "add_comment", generateOutputSchema = false,
            description = "Adds a comment to a page in the user's name (plain text, up to 4000 characters).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public McpModels.Comment addComment(
            @McpToolParam(description = "Page id", required = true) int page,
            @McpToolParam(description = "Comment text", required = true) String text) {
        return support.write("add_comment", () -> {
            readable(page);
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isEmpty()) throw new McpToolException("Write a comment first.");
            if (trimmed.length() > MAX_COMMENT_LENGTH) throw new McpToolException("Comments are limited to 4000 characters.");
            int id = comments.create(new PageId(page), McpToolSupport.userId(), trimmed);
            var created = comments.findById(id).orElseThrow();
            return new McpModels.Comment(id, created.getAuthorDisplayName(), McpModels.time(created.getCreateDate()), created.getContent());
        });
    }

    // --- helpers ------------------------------------------------------------------------------------

    private boolean canView(int pageId) {
        return pageId > 0 && permissions.hasUserPermission(new PageId(pageId), McpToolSupport.userId(), PagePermission.PermissionLevel.VIEW);
    }

    /** The page when the user may read it; hidden and deleted pages look the same as missing ones. */
    private Page readable(int pageId) {
        if (!canView(pageId)) throw new McpToolException("The page was not found or you may not read it.");
        try {
            Page page = pages.findById(new PageId(pageId));
            if (page == null || Boolean.TRUE.equals(page.getDeleted())) throw new McpToolException("The page was not found or you may not read it.");
            return page;
        } catch (EmptyResultDataAccessException e) {
            throw new McpToolException("The page was not found or you may not read it.");
        }
    }

    private void requireSpace(int spaceId) {
        if (spaces.spaces().find(spaceId).isEmpty() || !spaces.canView(spaceId))
            throw new McpToolException("The space was not found or you may not enter it.");
    }

    private McpModels.PageDetail detail(Page page) {
        int id = page.getPageId().value();
        var breadcrumb = new ArrayList<>(path(id));
        if (!breadcrumb.isEmpty() && breadcrumb.getLast().id() == id) breadcrumb.removeLast();
        boolean canEdit = permissions.hasUserPermission(page.getPageId(), McpToolSupport.userId(), PagePermission.PermissionLevel.EDIT);
        return new McpModels.PageDetail(id, page.getName(), page.getRevision(), pageUrl(id), spaceRef(page.getSpaceId()),
                breadcrumb, labels.listForPage(page.getPageId()), page.getContent(), page.getChangedByLogin(),
                McpModels.time(page.getChangeDate()), canEdit, comments.countForPage(page.getPageId()));
    }

    /** Root-to-page path limited to pages the user may see. */
    private List<McpModels.PageRef> path(Integer pageId) {
        if (pageId == null || pageId <= 0) return List.of();
        return pages.getPageHierarchy(new PageId(pageId)).stream()
                .filter(ancestor -> canView(ancestor.getPageId().value())).map(McpModels.PageRef::of).toList();
    }

    private McpModels.SpaceRef spaceRef(Integer spaceId) {
        if (spaceId == null) return null;
        return spaces.spaces().find(spaceId).map(space -> new McpModels.SpaceRef(space.getId(), space.getKey(), space.getName())).orElse(null);
    }

    private String pageUrl(int id) {
        String origin = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? urls.origin(attributes.getRequest()) : urls.configured().orElse("");
        return origin + "/ui/page/" + id;
    }

    private static String trim(String name) {
        if (name == null || name.isBlank()) throw new McpToolException("Enter a page name.");
        if (name.trim().length() > 255) throw new McpToolException("Page names are limited to 255 characters.");
        return name.trim();
    }

    private static List<String> labels(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > 32) throw new McpToolException("A page takes at most 32 labels.");
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(label -> !label.isEmpty()).toList();
    }

}
