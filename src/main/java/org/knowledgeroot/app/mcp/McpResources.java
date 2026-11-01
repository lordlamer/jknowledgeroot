package org.knowledgeroot.app.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.space.Space;
import org.knowledgeroot.app.space.SpaceService;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.knowledgeroot.app.mcp.McpToolSupport.params;

/**
 * Pages and spaces as MCP resources, so that clients can attach them as context without a tool
 * call: {@code knowledgeroot://page/{id}} is the page as Markdown, {@code knowledgeroot://space/{id}}
 * an overview of a space with its top-level pages.
 */
@Component
@RequiredArgsConstructor
public class McpResources {
    private final McpToolSupport support;
    private final McpPages access;
    private final SpaceService spaces;
    private final PageDao pages;

    @McpResource(uri = "knowledgeroot://page/{id}", name = "page", title = "Knowledgeroot page", mimeType = "text/markdown",
            description = "A page as Markdown with its name, space, breadcrumb, labels and last change.")
    public McpSchema.ReadResourceResult page(String id) {
        String uri = "knowledgeroot://page/" + id;
        return support.resource("page", params("id", id), () -> {
            Page page = access.readable(pageId(id));
            var detail = access.detail(page, McpMarkdown.MARKDOWN);
            var text = new StringBuilder("# ").append(detail.name()).append("\n\n");
            if (detail.space() != null) text.append("Space: ").append(detail.space().key()).append(" · ").append(detail.space().name()).append("  \n");
            if (!detail.breadcrumb().isEmpty()) text.append("Path: ").append(String.join(" / ", detail.breadcrumb().stream().map(McpModels.PageRef::name).toList())).append("  \n");
            if (!detail.labels().isEmpty()) text.append("Labels: ").append(String.join(", ", detail.labels())).append("  \n");
            text.append("Changed: ").append(detail.changedAt()).append(detail.changedBy() == null ? "" : " by " + detail.changedBy())
                    .append("  \nLink: ").append(detail.url()).append("  \nRevision: ").append(detail.revision()).append("\n\n")
                    .append(detail.content()).append('\n');
            return new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(uri, "text/markdown", text.toString())));
        });
    }

    @McpResource(uri = "knowledgeroot://space/{id}", name = "space", title = "Knowledgeroot space", mimeType = "text/markdown",
            description = "A space with its description and top-level pages.")
    public McpSchema.ReadResourceResult space(String id) {
        String uri = "knowledgeroot://space/" + id;
        return support.resource("space", params("id", id), () -> {
            int spaceId = access.requireSpace(pageId(id));
            Space space = spaces.space(spaceId);
            var text = new StringBuilder("# ").append(space.getKey()).append(" · ").append(space.getName()).append("\n\n");
            if (space.getDescription() != null && !space.getDescription().isBlank()) text.append(space.getDescription()).append("\n\n");
            text.append("## Top-level pages\n\n");
            for (Page page : pages.listNavigationPages(spaceId, 0, 0, McpToolSupport.userId()).stream().limit(50).toList()) {
                text.append("- ").append(page.getName()).append(" (page ").append(page.getPageId().value()).append(", ")
                        .append(access.pageUrl(page.getPageId().value())).append(")\n");
            }
            return new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(uri, "text/markdown", text.toString())));
        });
    }

    private static int pageId(String id) {
        try {
            return Integer.parseInt(id.trim());
        } catch (NumberFormatException e) {
            throw new McpToolException("The id in the resource URI must be a number.");
        }
    }
}
