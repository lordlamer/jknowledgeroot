package org.knowledgeroot.app.mcp;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.frontend.web.SearchController;
import org.knowledgeroot.app.page.api.PageDto;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageCommentDao;
import org.knowledgeroot.app.page.domain.PageCreationService;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageDraft;
import org.knowledgeroot.app.page.domain.PageDraftDao;
import org.knowledgeroot.app.page.domain.PageEditingService;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PageLabelDao;
import org.knowledgeroot.app.page.domain.PageMoveService;
import org.knowledgeroot.app.page.domain.PageStarDao;
import org.knowledgeroot.app.security.auth.CredentialRepository;
import org.knowledgeroot.app.space.Space;
import org.knowledgeroot.app.space.SpaceService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.knowledgeroot.app.mcp.McpToolSupport.params;

/**
 * The page tools an AI assistant gets over {@code /mcp}. Every tool runs as the user behind the
 * access token with the normal space and page permissions; changing tools additionally need the
 * {@code mcp:write} scope the user granted on the consent page. Attachments and history live in
 * {@link McpContentTools}.
 */
@Component
@RequiredArgsConstructor
public class McpTools {
    private static final int PAGE_SIZE = 20;
    private static final int TREE_SIZE = 50;
    private static final int TREE_BUDGET = 500;
    private static final int MAX_COMMENT_LENGTH = 4000;
    private final McpToolSupport support;
    private final McpPages access;
    private final SpaceService spaces;
    private final PageDao pages;
    private final PageLabelDao labels;
    private final PageStarDao stars;
    private final PageCommentDao comments;
    private final PageCreationService creation;
    private final PageEditingService editing;
    private final PageMoveService moves;
    private final PageDraftDao drafts;
    private final CredentialRepository credentials;

    // --- spaces -------------------------------------------------------------------------------------

    @McpTool(name = "list_spaces", generateOutputSchema = false,
            description = "Lists the spaces (separate page trees) the user may enter, with id, key, name and description. "
                    + "Pass a space id to search_pages, list_pages or get_tree to stay inside one space.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<McpModels.Space> listSpaces() {
        return support.read("list_spaces", () -> spaces.visibleSpaces().stream().map(McpModels.Space::of).toList());
    }

    @McpTool(name = "get_space", generateOutputSchema = false,
            description = "Details of one space: description, home page, whether the user may create top-level pages or manage it.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.SpaceDetail getSpace(@McpToolParam(description = "Space id", required = true) int id) {
        return support.read("get_space", params("id", id), () -> {
            access.requireSpace(id);
            Space space = spaces.space(id);
            return new McpModels.SpaceDetail(space.getId(), space.getKey(), space.getName(), space.getDescription(), space.isArchived(),
                    space.getHomePageId(), spaces.canCreateRootPage(id), spaces.canManage(id), access.origin() + "/spaces/" + id);
        });
    }

    // --- reading ------------------------------------------------------------------------------------

    @McpTool(name = "search_pages", generateOutputSchema = false,
            description = "Full-text search over page names and contents the user may read. Returns up to 20 hits with id, "
                    + "name, space, path and a snippet; use start for the next 20. Optional filters: space, label, "
                    + "changed_since (date) and changed_by (login). Call get_page for the full content.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.SearchResult searchPages(
            @McpToolParam(description = "Words to search for", required = true) String query,
            @McpToolParam(description = "Only search this space (id from list_spaces)", required = false) Integer space,
            @McpToolParam(description = "Offset of the first hit (0, 20, 40, ...)", required = false) Integer start,
            @McpToolParam(description = "Only pages carrying this label", required = false) String label,
            @McpToolParam(description = "Only pages changed on or after this date (YYYY-MM-DD)", required = false) String changed_since,
            @McpToolParam(description = "Only pages last changed by this login", required = false) String changed_by) {
        return support.read("search_pages", params("query", query, "space", space, "start", start, "label", label,
                "changed_since", changed_since, "changed_by", changed_by), () -> {
            String term = query == null ? "" : query.trim();
            if (term.isEmpty()) throw new McpToolException("Pass a search term.");
            if (term.length() > 200) throw new McpToolException("The search term is limited to 200 characters.");
            int offset = McpPages.offset(start);
            if (space != null) access.requireSpace(space);
            List<Page> results = pages.listVisiblePages(filter(term, space, label, changed_since, changed_by)
                    .start(offset).limit(PAGE_SIZE + 1).build(), McpToolSupport.userId());
            var hits = results.stream().limit(PAGE_SIZE).map(page -> new McpModels.SearchHit(page.getPageId().value(),
                    page.getName(), access.pageUrl(page.getPageId().value()), access.spaceRef(page.getSpaceId()),
                    access.path(page.getParent()).stream().map(McpModels.PageRef::name).toList(),
                    SearchController.snippet(page.getContent(), term), McpModels.time(page.getChangeDate()))).toList();
            return new McpModels.SearchResult(term, hits, offset, results.size() > PAGE_SIZE);
        });
    }

    @McpTool(name = "get_page", generateOutputSchema = false,
            description = "Reads one page: name, space, breadcrumb, labels, the content (Markdown by default, or HTML with "
                    + "format=\"html\"), the revision needed for update_page and a link for the user.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.PageDetail getPage(@McpToolParam(description = "Page id", required = true) int id,
                                        @McpToolParam(description = "Content format: markdown (default) or html", required = false) String format) {
        return support.read("get_page", params("id", id, "format", format), () -> access.detail(access.readable(id), McpMarkdown.format(format)));
    }

    @McpTool(name = "list_pages", generateOutputSchema = false,
            description = "Lists the child pages of a page, or the top-level pages of a space when parent is omitted "
                    + "(space defaults to the default space). Up to 50 entries; hasChildren says where to descend.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.PageList listPages(
            @McpToolParam(description = "Parent page id; omit for the top level of a space", required = false) Integer parent,
            @McpToolParam(description = "Space id for the top level (from list_spaces)", required = false) Integer space) {
        return support.read("list_pages", params("parent", parent, "space", space), () -> {
            Integer viewer = McpToolSupport.userId();
            McpModels.PageRef parentRef = null;
            int spaceId;
            if (parent != null && parent > 0) {
                Page parentPage = access.readable(parent);
                parentRef = McpModels.PageRef.of(parentPage);
                spaceId = parentPage.getSpaceId() == null ? spaces.defaultSpace().getId() : parentPage.getSpaceId();
            } else {
                spaceId = access.requireSpace(space);
            }
            List<Page> visible = pages.listNavigationPages(spaceId, parentRef == null ? 0 : parentRef.id(), 0, viewer);
            List<Page> items = visible.stream().limit(TREE_SIZE).toList();
            Set<Integer> withChildren = pages.parentsWithVisibleChildren(
                    items.stream().map(page -> page.getPageId().value()).toList(), viewer);
            return new McpModels.PageList(parentRef, access.spaceRef(spaceId), items.stream().map(page -> new McpModels.PageNode(
                    page.getPageId().value(), page.getName(), withChildren.contains(page.getPageId().value()),
                    access.pageUrl(page.getPageId().value()))).toList(), visible.size() > TREE_SIZE);
        });
    }

    @McpTool(name = "get_tree", generateOutputSchema = false,
            description = "The page hierarchy below a page (or the top level of a space) as nested nodes, up to depth levels "
                    + "(default 2, at most 4) and 500 nodes. Use it for overviews instead of many list_pages calls.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.Tree getTree(
            @McpToolParam(description = "Root page id; omit for the top level of a space", required = false) Integer parent,
            @McpToolParam(description = "Space id for the top level (from list_spaces)", required = false) Integer space,
            @McpToolParam(description = "Levels to descend, 1 to 4 (default 2)", required = false) Integer depth) {
        return support.read("get_tree", params("parent", parent, "space", space, "depth", depth), () -> {
            int levels = depth == null ? 2 : Math.max(1, Math.min(4, depth));
            McpModels.PageRef root = null;
            int spaceId;
            if (parent != null && parent > 0) {
                Page rootPage = access.readable(parent);
                root = McpModels.PageRef.of(rootPage);
                spaceId = rootPage.getSpaceId() == null ? spaces.defaultSpace().getId() : rootPage.getSpaceId();
            } else {
                spaceId = access.requireSpace(space);
            }
            int[] budget = {TREE_BUDGET};
            boolean[] truncated = {false};
            var nodes = subtree(spaceId, root == null ? 0 : root.id(), levels, budget, truncated);
            return new McpModels.Tree(root, access.spaceRef(spaceId), nodes, truncated[0]);
        });
    }

    private List<McpModels.TreeNode> subtree(int spaceId, int parent, int levels, int[] budget, boolean[] truncated) {
        Integer viewer = McpToolSupport.userId();
        List<Page> visible = pages.listNavigationPages(spaceId, parent, 0, viewer);
        List<Page> items = visible.stream().limit(TREE_SIZE).toList();
        if (visible.size() > TREE_SIZE) truncated[0] = true;
        Set<Integer> withChildren = pages.parentsWithVisibleChildren(items.stream().map(page -> page.getPageId().value()).toList(), viewer);
        var nodes = new ArrayList<McpModels.TreeNode>();
        for (Page page : items) {
            if (budget[0]-- <= 0) {
                truncated[0] = true;
                break;
            }
            int id = page.getPageId().value();
            boolean more = withChildren.contains(id);
            List<McpModels.TreeNode> children = more && levels > 1 ? subtree(spaceId, id, levels - 1, budget, truncated) : List.of();
            if (more && levels <= 1) truncated[0] = true;
            nodes.add(new McpModels.TreeNode(id, page.getName(), access.pageUrl(id), children, more && children.isEmpty()));
        }
        return nodes;
    }

    @McpTool(name = "list_starred", generateOutputSchema = false,
            description = "The pages the user starred as favourites, most recently starred first.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<McpModels.PageNode> listStarred() {
        return support.read("list_starred", () -> stars.listStarredPages(McpToolSupport.userId()).stream()
                .filter(page -> access.canView(page.getPageId().value()))
                .map(page -> new McpModels.PageNode(page.getPageId().value(), page.getName(), false, access.pageUrl(page.getPageId().value())))
                .toList());
    }

    @McpTool(name = "list_comments", generateOutputSchema = false,
            description = "The comments on a page, oldest first.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<McpModels.Comment> listComments(@McpToolParam(description = "Page id", required = true) int page) {
        return support.read("list_comments", params("page", page), () -> {
            access.readable(page);
            return comments.listForPage(new PageId(page)).stream().map(comment -> new McpModels.Comment(comment.getId(),
                    comment.getAuthorDisplayName(), McpModels.time(comment.getCreateDate()), comment.getContent())).toList();
        });
    }

    @McpTool(name = "recent_changes", generateOutputSchema = false,
            description = "Pages changed most recently, newest first, 20 per call; optionally only one space or only "
                    + "changes on or after a date (YYYY-MM-DD).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.ChangedPages recentChanges(
            @McpToolParam(description = "Space id (from list_spaces)", required = false) Integer space,
            @McpToolParam(description = "Only changes on or after this date (YYYY-MM-DD)", required = false) String since,
            @McpToolParam(description = "Offset of the first entry (0, 20, 40, ...)", required = false) Integer start) {
        return support.read("recent_changes", params("space", space, "since", since, "start", start), () -> {
            if (space != null) access.requireSpace(space);
            int offset = McpPages.offset(start);
            var filter = PageFilter.builder().spaceId(space).deleted(false).changeDateBegin(date(since)).newestFirst(true)
                    .start(offset).limit(PAGE_SIZE + 1).build();
            List<Page> results = pages.listVisiblePages(filter, McpToolSupport.userId());
            return new McpModels.ChangedPages(results.stream().limit(PAGE_SIZE).map(this::changed).toList(), offset, results.size() > PAGE_SIZE);
        });
    }

    @McpTool(name = "list_labels", generateOutputSchema = false,
            description = "Labels (tags) in use on pages the user may read, with the number of pages, optionally for one space.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<McpModels.Label> listLabels(@McpToolParam(description = "Space id (from list_spaces)", required = false) Integer space) {
        return support.read("list_labels", params("space", space), () -> {
            if (space != null) access.requireSpace(space);
            return labels.listLabels(space, McpToolSupport.userId()).stream()
                    .map(label -> new McpModels.Label(label.name(), label.pages())).toList();
        });
    }

    @McpTool(name = "pages_by_label", generateOutputSchema = false,
            description = "Pages carrying a label, newest change first, 20 per call; optionally only one space.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.ChangedPages pagesByLabel(
            @McpToolParam(description = "Label name", required = true) String label,
            @McpToolParam(description = "Space id (from list_spaces)", required = false) Integer space,
            @McpToolParam(description = "Offset of the first entry (0, 20, 40, ...)", required = false) Integer start) {
        return support.read("pages_by_label", params("label", label, "space", space, "start", start), () -> {
            if (label == null || label.isBlank()) throw new McpToolException("Pass a label.");
            if (space != null) access.requireSpace(space);
            int offset = McpPages.offset(start);
            var filter = PageFilter.builder().spaceId(space).label(label.trim()).deleted(false).newestFirst(true)
                    .start(offset).limit(PAGE_SIZE + 1).build();
            List<Page> results = pages.listVisiblePages(filter, McpToolSupport.userId());
            return new McpModels.ChangedPages(results.stream().limit(PAGE_SIZE).map(this::changed).toList(), offset, results.size() > PAGE_SIZE);
        });
    }

    // --- writing (scope mcp:write) -------------------------------------------------------------------

    @McpTool(name = "create_page", generateOutputSchema = false,
            description = "Creates a page below a parent page, or as a top-level page of a space when parent is omitted. "
                    + "Content is Markdown by default (headings, lists, links, tables, code) or HTML with format=\"html\"; "
                    + "scripts and styles are removed.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public McpModels.Saved createPage(
            @McpToolParam(description = "Page name (up to 255 characters)", required = true) String name,
            @McpToolParam(description = "Content in the given format", required = true) String content,
            @McpToolParam(description = "Parent page id; omit for a top-level page", required = false) Integer parent,
            @McpToolParam(description = "Space id for a top-level page; defaults to the default space", required = false) Integer space,
            @McpToolParam(description = "Labels (tags) for the page", required = false) List<String> labels,
            @McpToolParam(description = "Content format: markdown (default) or html", required = false) String format,
            @McpToolParam(description = "true creates a draft that only editors see until publish_page", required = false) Boolean draft) {
        return support.write("create_page", params("name", name, "content", content, "parent", parent, "space", space,
                "labels", labels, "format", format, "draft", draft), () -> {
            String contentFormat = McpMarkdown.format(format);
            var dto = PageDto.builder().parent(parent == null ? 0 : parent).spaceId(parent == null ? space : null)
                    .name(McpPages.trimName(name)).content(McpMarkdown.store(content, contentFormat))
                    .published(!Boolean.TRUE.equals(draft)).build();
            int id = creation.create(dto, McpPages.labels(labels));
            Page created = pages.findById(new PageId(id));
            return new McpModels.Saved(id, created.getName(), created.getRevision(), access.pageUrl(id),
                    created.isPublished() ? "Page created." : "Draft created; readers see it after publish_page.");
        });
    }

    @McpTool(name = "update_page", generateOutputSchema = false,
            description = "Saves a new name, content or labels of a page as its working copy, which readers do not see "
                    + "until it is published; publish=true publishes right away as a new version. Pass the revision from "
                    + "get_page; the save is refused when the page changed meanwhile. Omitted fields keep their current "
                    + "value. Content is Markdown by default or HTML with format=\"html\".",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public McpModels.Saved updatePage(
            @McpToolParam(description = "Page id", required = true) int id,
            @McpToolParam(description = "Revision from get_page", required = true) long revision,
            @McpToolParam(description = "New page name", required = false) String name,
            @McpToolParam(description = "New content in the given format", required = false) String content,
            @McpToolParam(description = "New labels; replaces the current ones", required = false) List<String> labels,
            @McpToolParam(description = "Content format: markdown (default) or html", required = false) String format,
            @McpToolParam(description = "true publishes as a new version; otherwise only the working copy changes", required = false) Boolean publish) {
        return support.write("update_page", params("id", id, "revision", revision, "name", name, "content", content,
                "labels", labels, "format", format, "publish", publish), () -> {
            Page current = access.editable(id);
            String contentFormat = McpMarkdown.format(format);
            PageId pageId = new PageId(id);
            // Omitted fields continue the working copy when there is one, otherwise the published page.
            var base = drafts.find(pageId);
            String baseName = base.map(PageDraft::name).orElse(current.getName());
            String baseContent = base.map(PageDraft::content).orElse(current.getContent());
            List<String> baseLabels = base.map(PageDraft::labels).orElseGet(() -> this.labels.listForPage(pageId));
            String newName = name == null ? baseName : McpPages.trimName(name);
            String newContent = content == null ? baseContent : McpMarkdown.store(content, contentFormat);
            List<String> newLabels = labels == null ? baseLabels : McpPages.labels(labels);
            if (Boolean.TRUE.equals(publish)) {
                var dto = PageDto.builder().id(id).name(newName).content(newContent).revision(revision).published(true).build();
                editing.edit(pageId, dto, newLabels, Map.of(), List.of(), List.of());
                Page saved = pages.findById(pageId);
                return new McpModels.Saved(id, saved.getName(), saved.getRevision(), access.pageUrl(id), "Page published.");
            }
            editing.saveDraft(pageId, newName, newContent, newLabels, revision);
            return new McpModels.Saved(id, newName, current.getRevision(), access.pageUrl(id),
                    "Working copy saved; readers see it after publish_page or update_page with publish=true.");
        });
    }

    @McpTool(name = "publish_page", generateOutputSchema = false,
            description = "Publishes the working copy of a page as a new version, or makes a draft page visible to readers. "
                    + "Pass the revision from get_page.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public McpModels.Saved publishPage(
            @McpToolParam(description = "Page id", required = true) int id,
            @McpToolParam(description = "Revision from get_page", required = true) long revision) {
        return support.write("publish_page", params("id", id, "revision", revision), () -> {
            access.editable(id);
            editing.publish(new PageId(id), revision);
            Page saved = pages.findById(new PageId(id));
            return new McpModels.Saved(id, saved.getName(), saved.getRevision(), access.pageUrl(id), "Page published.");
        });
    }

    @McpTool(name = "set_labels", generateOutputSchema = false,
            description = "Replaces the labels (tags) of a page; name and content stay. Pass the revision from get_page.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public McpModels.Saved setLabels(
            @McpToolParam(description = "Page id", required = true) int id,
            @McpToolParam(description = "Revision from get_page", required = true) long revision,
            @McpToolParam(description = "The complete new list of labels", required = true) List<String> labels) {
        return support.write("set_labels", params("id", id, "revision", revision, "labels", labels), () -> {
            Page current = access.editable(id);
            var dto = PageDto.builder().id(id).name(current.getName()).content(current.getContent()).revision(revision).build();
            editing.edit(new PageId(id), dto, McpPages.labels(labels), Map.of(), List.of(), List.of());
            Page saved = pages.findById(new PageId(id));
            return new McpModels.Saved(id, saved.getName(), saved.getRevision(), access.pageUrl(id), "Labels saved.");
        });
    }

    @McpTool(name = "add_comment", generateOutputSchema = false,
            description = "Adds a comment to a page in the user's name (plain text, up to 4000 characters).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public McpModels.Comment addComment(
            @McpToolParam(description = "Page id", required = true) int page,
            @McpToolParam(description = "Comment text", required = true) String text) {
        return support.write("add_comment", params("page", page, "text", text), () -> {
            access.readable(page);
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isEmpty()) throw new McpToolException("Write a comment first.");
            if (trimmed.length() > MAX_COMMENT_LENGTH) throw new McpToolException("Comments are limited to 4000 characters.");
            int id = comments.create(new PageId(page), McpToolSupport.userId(), trimmed);
            var created = comments.findById(id).orElseThrow();
            return new McpModels.Comment(id, created.getAuthorDisplayName(), McpModels.time(created.getCreateDate()), created.getContent());
        });
    }

    @McpTool(name = "delete_comment", generateOutputSchema = false,
            description = "Deletes a comment of the user (administrators: any comment) from a page.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public String deleteComment(
            @McpToolParam(description = "Page id", required = true) int page,
            @McpToolParam(description = "Comment id from list_comments", required = true) int comment_id) {
        return support.write("delete_comment", params("page", page, "comment_id", comment_id), () -> {
            access.readable(page);
            var comment = comments.findById(comment_id).filter(found -> found.getPageId().value() == page)
                    .orElseThrow(() -> new McpToolException("The comment was not found."));
            boolean own = comment.getUserId() != null && comment.getUserId() == McpToolSupport.userId();
            if (!own && !McpToolSupport.user().isAdmin()) throw new McpToolException("Only the author or an administrator can delete this comment.");
            comments.delete(comment_id);
            return "Comment deleted.";
        });
    }

    @McpTool(name = "star_page", generateOutputSchema = false,
            description = "Adds a page to the user's favourites, or removes it with starred=false.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String starPage(
            @McpToolParam(description = "Page id", required = true) int id,
            @McpToolParam(description = "true to star, false to unstar (default true)", required = false) Boolean starred) {
        return support.write("star_page", params("id", id, "starred", starred), () -> {
            access.readable(id);
            if (starred == null || starred) {
                stars.starPage(McpToolSupport.userId(), new PageId(id));
                return "Page starred.";
            }
            stars.unstarPage(McpToolSupport.userId(), new PageId(id));
            return "Star removed.";
        });
    }

    @McpTool(name = "delete_page", generateOutputSchema = false,
            description = "Moves a page to the deleted pages (its sub-pages stay). Pass the revision from get_page. "
                    + "restore_page brings it back.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public String deletePage(
            @McpToolParam(description = "Page id", required = true) int id,
            @McpToolParam(description = "Revision from get_page", required = true) long revision) {
        return support.write("delete_page", params("id", id, "revision", revision), () -> {
            Page page = access.editable(id);
            editing.delete(new PageId(id), revision);
            return "Page \"" + page.getName() + "\" deleted. " + (McpToolSupport.user().isAdmin()
                    ? "restore_page " + id + " brings it back." : "An administrator can bring it back with restore_page " + id + ".");
        });
    }

    @McpTool(name = "restore_page", generateOutputSchema = false,
            description = "Restores an earlier version of a page when history_id is given (from get_history), or, for "
                    + "administrators, a deleted page. Pass the current revision; get_history names it for a deleted page.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public McpModels.Saved restorePage(
            @McpToolParam(description = "Page id", required = true) int id,
            @McpToolParam(description = "Current revision of the page", required = true) long revision,
            @McpToolParam(description = "History id of the version to restore; omit for the latest saved state", required = false) Integer history_id) {
        return support.write("restore_page", params("id", id, "revision", revision, "history_id", history_id), () -> {
            Page page;
            try {
                page = pages.findById(new PageId(id));
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                throw new McpToolException(McpPages.NOT_READABLE);
            }
            if (page == null) throw new McpToolException(McpPages.NOT_READABLE);
            if (Boolean.TRUE.equals(page.getDeleted())) {
                if (!McpToolSupport.user().isAdmin()) throw new McpToolException("Only an administrator can restore a deleted page.");
            } else if (!access.canEdit(id)) {
                throw new McpToolException(McpPages.NOT_READABLE);
            }
            int version = history_id != null ? history_id : pages.listRevisions(new PageId(id), 0).stream().findFirst()
                    .orElseThrow(() -> new McpToolException("The page has no saved versions.")).id();
            editing.restore(new PageId(id), version, revision);
            Page restored = pages.findById(new PageId(id));
            return new McpModels.Saved(id, restored.getName(), restored.getRevision(), access.pageUrl(id), "Page restored.");
        });
    }

    @McpTool(name = "move_page", generateOutputSchema = false,
            description = "Moves a page with its sub-pages below another page (parent) or to the top level (parent 0), "
                    + "optionally to the top level of another space. Pass the revision from get_page.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.Saved movePage(
            @McpToolParam(description = "Page id", required = true) int id,
            @McpToolParam(description = "New parent page id, or 0 for the top level", required = true) int parent,
            @McpToolParam(description = "Revision from get_page", required = true) long revision,
            @McpToolParam(description = "Space id for the top level of another space", required = false) Integer space) {
        return support.write("move_page", params("id", id, "parent", parent, "revision", revision, "space", space), () -> {
            access.editable(id);
            if (parent > 0) access.readable(parent);
            moves.move(new PageId(id), parent, revision, space);
            Page moved = pages.findById(new PageId(id));
            return new McpModels.Saved(id, moved.getName(), moved.getRevision(), access.pageUrl(id), "Page moved.");
        });
    }

    // --- helpers ------------------------------------------------------------------------------------

    private McpModels.ChangedPage changed(Page page) {
        int id = page.getPageId().value();
        return new McpModels.ChangedPage(id, page.getName(), access.pageUrl(id), access.spaceRef(page.getSpaceId()),
                page.getRevision(), page.getChangedByLogin(), McpModels.time(page.getChangeDate()));
    }

    private PageFilter.PageFilterBuilder filter(String term, Integer space, String label, String changedSince, String changedBy) {
        var filter = PageFilter.builder().query(term).spaceId(space).deleted(false);
        if (label != null && !label.isBlank()) filter.label(label.trim());
        filter.changeDateBegin(date(changedSince));
        if (changedBy != null && !changedBy.isBlank()) {
            var account = credentials.findByLogin(changedBy.trim().toLowerCase(java.util.Locale.ROOT));
            // An unknown login matches nothing rather than everything.
            filter.changedBy(account.map(credential -> Integer.valueOf(credential.userId())).orElse(-1));
        }
        return filter;
    }

    private static LocalDateTime date(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new McpToolException("Dates are written as YYYY-MM-DD.");
        }
    }
}
