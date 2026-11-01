package org.knowledgeroot.app.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.knowledgeroot.app.mcp.McpToolSupport.params;

/** Ready-made prompts clients offer to the user, each a recipe over the tools. */
@Component
@RequiredArgsConstructor
public class McpPrompts {
    private final McpToolSupport support;

    @McpPrompt(name = "summarize_space", title = "Summarise a space",
            description = "An overview of what a space contains, built from its page tree and the most important pages.")
    public McpSchema.GetPromptResult summarizeSpace(
            @McpArg(name = "space", description = "Space id or key from list_spaces", required = true) String space) {
        return support.prompt("summarize_space", params("space", space), () -> result("Summary of a Knowledgeroot space", """
                Summarise the Knowledgeroot space "%s" for me.
                1. Call list_spaces to find the space (by id or key) and get_space for its description.
                2. Call get_tree with depth 3 for the space to see its structure.
                3. Read the top-level pages and the ones that look central with get_page.
                4. Write a structured summary: purpose of the space, main topics with links, pages that seem outdated
                   (recent_changes helps) or empty, and open questions a newcomer would have.
                """.formatted(space)));
    }

    @McpPrompt(name = "find_stale_pages", title = "Find stale pages",
            description = "Pages that have not been changed for a long time and may need a review.")
    public McpSchema.GetPromptResult findStalePages(
            @McpArg(name = "space", description = "Space id from list_spaces; omit for all spaces", required = false) String space,
            @McpArg(name = "months", description = "Pages older than this many months count as stale (default 12)", required = false) String months) {
        String period = months == null || months.isBlank() ? "12" : months.trim();
        return support.prompt("find_stale_pages", params("space", space, "months", months), () -> result("Stale pages in Knowledgeroot", """
                Find pages in Knowledgeroot%s that were last changed more than %s months ago.
                1. Use get_tree (depth 4) %sto list the pages, then get_page for their last change date where needed;
                   recent_changes shows what is fresh.
                2. List the stale pages with link, last change and a one-line guess why they may be outdated.
                3. Suggest for each: keep, update, merge into another page, or delete. Do not change anything yet.
                """.formatted(space == null || space.isBlank() ? "" : " in space " + space, period,
                space == null || space.isBlank() ? "per space from list_spaces " : "for space " + space + " ")));
    }

    @McpPrompt(name = "draft_page", title = "Draft a page",
            description = "Drafts a new page on a topic from what Knowledgeroot already knows, for the user to approve.")
    public McpSchema.GetPromptResult draftPage(
            @McpArg(name = "topic", description = "What the page should be about", required = true) String topic,
            @McpArg(name = "parent", description = "Page id the new page goes below; omit for a top-level page", required = false) String parent) {
        return support.prompt("draft_page", params("topic", topic, "parent", parent), () -> result("Draft a Knowledgeroot page", """
                Draft a Knowledgeroot page about "%s".
                1. Call search_pages for the topic and read the most relevant hits with get_page, so the draft fits
                   existing content and links to it instead of repeating it.
                2. Write the draft as Markdown: a short intro, clear headings, lists where useful, links to related
                   pages as their Knowledgeroot URLs.
                3. Show me the draft and the labels you suggest. Only after I approve, call create_page%s.
                """.formatted(topic, parent == null || parent.isBlank() ? " (ask me for the parent page or space)" : " with parent " + parent)));
    }

    private static McpSchema.GetPromptResult result(String description, String text) {
        return new McpSchema.GetPromptResult(description,
                List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(text.strip()))));
    }
}
