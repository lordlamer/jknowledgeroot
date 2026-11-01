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

    public record SpaceRef(int id, String key, String name) {}

    public record PageRef(int id, String name) {
        static PageRef of(Page page) {
            return new PageRef(page.getPageId().value(), page.getName());
        }
    }

    public record PageNode(int id, String name, boolean hasChildren, String url) {}

    public record PageList(PageRef parent, SpaceRef space, List<PageNode> pages, boolean hasMore) {}

    public record SearchHit(int id, String name, String url, SpaceRef space, List<String> path, String snippet, String changedAt) {}

    public record SearchResult(String query, List<SearchHit> hits, int start, boolean hasMore) {}

    public record PageDetail(int id, String name, long revision, String url, SpaceRef space, List<PageRef> breadcrumb,
                             List<String> labels, String contentHtml, String changedBy, String changedAt,
                             boolean canEdit, int comments) {}

    public record Comment(int id, String author, String createdAt, String text) {}

    public record Saved(int id, String name, long revision, String url, String message) {}

    static String time(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
