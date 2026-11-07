package org.knowledgeroot.app.mcp;

import org.knowledgeroot.app.page.domain.Page;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** What the MCP tools return to the assistant. Field names are part of the tool contract. */
public final class McpModels {
    private McpModels() {}

    public record Space(int id, String key, String name, String description, boolean archived) {
        static Space of(org.knowledgeroot.app.space.Space space) {
            return new Space(space.getId(), space.getKey(), space.getName(), space.getDescription(), space.isArchived());
        }
    }

    public record SpaceDetail(int id, String key, String name, String description, boolean archived, Integer homePageId,
                              boolean canCreateRootPage, boolean canManage, String url) {}

    public record SpaceRef(int id, String key, String name) {}

    public record PageRef(int id, String name) {
        static PageRef of(Page page) {
            return new PageRef(page.getPageId().value(), page.getName());
        }
    }

    public record PageNode(int id, String name, boolean hasChildren, String url) {}

    public record PageList(PageRef parent, SpaceRef space, List<PageNode> pages, boolean hasMore) {}

    /** A subtree; {@code truncated} says that the node budget or the depth cut the listing. */
    public record TreeNode(int id, String name, String url, List<TreeNode> children, boolean hasMore) {}

    public record Tree(PageRef root, SpaceRef space, List<TreeNode> pages, boolean truncated) {}

    public record SearchHit(int id, String name, String url, SpaceRef space, List<String> path, String snippet, String changedAt) {}

    public record SearchResult(String query, List<SearchHit> hits, int start, boolean hasMore) {}

    public record ChangedPage(int id, String name, String url, SpaceRef space, long revision, String changedBy, String changedAt) {}

    public record ChangedPages(List<ChangedPage> pages, int start, boolean hasMore) {}

    public record Label(String name, int pages) {}

    public record PageDetail(int id, String name, long revision, String url, SpaceRef space, List<PageRef> breadcrumb,
                             List<String> labels, String format, String content, String changedBy, String changedAt,
                             boolean canEdit, int comments, int attachments, boolean published, boolean hasWorkingCopy) {}

    public record Comment(int id, String author, String createdAt, String text) {}

    public record Saved(int id, String name, long revision, String url, String message) {}

    public record Attachment(int id, int pageId, String name, Integer size, String type, String createdAt, String url) {}

    /** {@code text} is the extracted text, or null with {@code note} explaining why there is none. */
    public record AttachmentContent(Attachment attachment, String text, boolean truncated, String note) {}

    public record Revision(int historyId, long revision, String name, String changedAt) {}

    public record History(PageRef page, long currentRevision, List<Revision> revisions, int start, boolean hasMore) {}

    public record RevisionContent(PageRef page, int historyId, long revision, String name, String changedAt,
                                  List<String> labels, String format, String content) {}

    public record DiffPart(String kind, String text) {}

    public record Comparison(PageRef page, String from, String to, boolean changed, List<DiffPart> nameDiff, List<DiffPart> textDiff) {}

    static String time(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
