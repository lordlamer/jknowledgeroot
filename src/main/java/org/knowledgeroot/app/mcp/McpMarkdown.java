package org.knowledgeroot.app.mcp;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.List;
import java.util.Locale;

/**
 * Page content for assistants: pages are stored as HTML, assistants prefer Markdown. Reading
 * converts HTML to Markdown unless HTML is asked for; writing accepts Markdown (default) or HTML.
 * The sanitizer of the page service still runs on every save.
 */
public final class McpMarkdown {
    public static final String MARKDOWN = "markdown";
    public static final String HTML = "html";
    private static final MutableDataSet OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
    private static final Parser PARSER = Parser.builder(OPTIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder(OPTIONS).build();
    // ATX headings and dash bullets: the Markdown assistants write themselves.
    private static final FlexmarkHtmlConverter CONVERTER = FlexmarkHtmlConverter.builder(new MutableDataSet(OPTIONS)
            .set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false)
            .set(FlexmarkHtmlConverter.UNORDERED_LIST_DELIMITER, '-')).build();

    private McpMarkdown() {}

    /** Normalises the {@code format} parameter of a tool: markdown unless html was asked for. */
    public static String format(String value) {
        if (value == null || value.isBlank()) return MARKDOWN;
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (lower.equals(HTML) || lower.equals(MARKDOWN)) return lower;
        throw new McpToolException("format must be \"markdown\" or \"html\".");
    }

    /** Content as the assistant wants it. */
    public static String render(String html, String format) {
        if (html == null) return "";
        return HTML.equals(format) ? html : toMarkdown(html);
    }

    /** Content as the page store wants it. */
    public static String store(String content, String format) {
        if (content == null) return "";
        return HTML.equals(format) ? content : toHtml(content);
    }

    public static String toHtml(String markdown) {
        return RENDERER.render(PARSER.parse(markdown)).trim();
    }

    public static String toMarkdown(String html) {
        return CONVERTER.convert(html).trim();
    }
}
