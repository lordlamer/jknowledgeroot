package org.knowledgeroot.app.page.export;

import org.jsoup.nodes.Element;
import org.knowledgeroot.app.mcp.McpMarkdown;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Markdown output: the title as heading, the metadata as a list, then the content through the HTML converter. */
@Component
public class MarkdownWriter {

    public byte[] write(ExportDocument document, ExportText text) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(document.title()).append("\n\n");
        for (String line : text.metaLines(document)) out.append("- ").append(line).append('\n');
        out.append("\n---\n\n");
        Element copy = document.body().clone();
        // Images keep their address; a reader of the Markdown file resolves it against the installation.
        for (Element img : copy.select("img")) img.removeAttr(ExportDocument.IMAGE_ATTRIBUTE);
        out.append(McpMarkdown.toMarkdown(copy.html())).append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
