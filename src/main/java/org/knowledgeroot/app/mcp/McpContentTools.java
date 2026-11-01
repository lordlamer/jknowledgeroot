package org.knowledgeroot.app.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PageLabelDao;
import org.knowledgeroot.app.page.domain.PageRevision;
import org.knowledgeroot.app.page.domain.RevisionDiff;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.knowledgeroot.app.mcp.McpToolSupport.params;

/** Attachments and page history for assistants; see {@link McpTools} for the page tools. */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpContentTools {
    /** Attachments larger than this are not read into memory for text extraction. */
    static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    static final int DEFAULT_CHARS = 20_000;
    static final int MAX_CHARS = 100_000;
    private final McpToolSupport support;
    private final McpPages access;
    private final PageDao pages;
    private final PageLabelDao labels;
    private final FileDao files;

    // --- attachments ----------------------------------------------------------------------------------

    @McpTool(name = "list_attachments", generateOutputSchema = false,
            description = "The attachments of a page with id, name, size, type and a download link for the user. "
                    + "get_attachment reads the text of text, Markdown, CSV, JSON, XML, HTML and PDF files.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<McpModels.Attachment> listAttachments(@McpToolParam(description = "Page id", required = true) int page) {
        return support.read("list_attachments", params("page", page), () -> {
            access.readable(page);
            return files.listFiles(FileFilter.builder().pageId(new PageId(page)).build()).stream().map(this::attachment).toList();
        });
    }

    @McpTool(name = "get_attachment", generateOutputSchema = false,
            description = "Reads the text of an attachment (text, Markdown, CSV, JSON, XML, HTML, PDF), up to max_chars "
                    + "characters (default 20000). Other file types only return their details and link.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.AttachmentContent getAttachment(
            @McpToolParam(description = "Attachment id from list_attachments", required = true) int id,
            @McpToolParam(description = "Maximum characters to return (up to 100000)", required = false) Integer max_chars) {
        return support.read("get_attachment", params("id", id, "max_chars", max_chars), () -> {
            File file = attachmentOf(id);
            access.readable(file.getPageId());
            int limit = max_chars == null || max_chars <= 0 ? DEFAULT_CHARS : Math.min(max_chars, MAX_CHARS);
            String kind = textKind(file);
            if (kind == null) return new McpModels.AttachmentContent(attachment(file), null, false,
                    "No text is extracted for this file type; give the user the link instead.");
            if (file.getSize() != null && file.getSize() > MAX_ATTACHMENT_BYTES) return new McpModels.AttachmentContent(attachment(file), null, false,
                    "The file is larger than 10 MB and is not read; give the user the link instead.");
            String text;
            try (InputStream content = files.loadFile(file.getId())) {
                if (content == null) throw new McpToolException("The attachment's file is missing.");
                byte[] bytes = content.readNBytes((int) MAX_ATTACHMENT_BYTES + 1);
                if (bytes.length > MAX_ATTACHMENT_BYTES) return new McpModels.AttachmentContent(attachment(file), null, false,
                        "The file is larger than 10 MB and is not read; give the user the link instead.");
                text = kind.equals("pdf") ? pdfText(bytes) : new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("MCP could not read attachment {}", id, e);
                throw new McpToolException("The attachment could not be read.");
            }
            if (kind.equals("html")) text = McpMarkdown.toMarkdown(text);
            text = text.strip();
            boolean truncated = text.length() > limit;
            return new McpModels.AttachmentContent(attachment(file), truncated ? text.substring(0, limit) : text, truncated,
                    truncated ? "The text was cut after " + limit + " characters; raise max_chars for more." : null);
        });
    }

    @McpTool(name = "delete_attachment", generateOutputSchema = false,
            description = "Deletes an attachment from a page the user may edit.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public String deleteAttachment(@McpToolParam(description = "Attachment id from list_attachments", required = true) int id) {
        return support.write("delete_attachment", params("id", id), () -> {
            File file = attachmentOf(id);
            access.editable(file.getPageId());
            files.deleteFileById(id);
            return "Attachment \"" + file.getName() + "\" deleted.";
        });
    }

    // --- history --------------------------------------------------------------------------------------

    @McpTool(name = "get_history", generateOutputSchema = false,
            description = "The saved versions of a page, newest first, 50 per call, with the history id for get_revision, "
                    + "compare_revisions and restore_page. Administrators also see deleted pages.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.History getHistory(
            @McpToolParam(description = "Page id", required = true) int page,
            @McpToolParam(description = "Offset of the first version (0, 50, 100, ...)", required = false) Integer start) {
        return support.read("get_history", params("page", page, "start", start), () -> {
            Page current = historyPage(page);
            int offset = McpPages.offset(start);
            List<PageRevision> revisions = pages.listRevisions(new PageId(page), offset);
            return new McpModels.History(McpModels.PageRef.of(current), current.getRevision(), revisions.stream().limit(50)
                    .map(revision -> new McpModels.Revision(revision.id(), revision.revision(), revision.name(), McpModels.time(revision.changedAt()))).toList(),
                    offset, revisions.size() > 50);
        });
    }

    @McpTool(name = "get_revision", generateOutputSchema = false,
            description = "An earlier version of a page by history id (from get_history), content as Markdown or HTML.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.RevisionContent getRevision(
            @McpToolParam(description = "Page id", required = true) int page,
            @McpToolParam(description = "History id from get_history", required = true) int history_id,
            @McpToolParam(description = "Content format: markdown (default) or html", required = false) String format) {
        return support.read("get_revision", params("page", page, "history_id", history_id, "format", format), () -> {
            Page current = historyPage(page);
            String contentFormat = McpMarkdown.format(format);
            PageRevision revision = revision(page, history_id);
            return new McpModels.RevisionContent(McpModels.PageRef.of(current), revision.id(), revision.revision(), revision.name(),
                    McpModels.time(revision.changedAt()), revision.labelsCaptured() ? revision.labels() : labels.listForPage(new PageId(page)),
                    contentFormat, McpMarkdown.render(revision.content(), contentFormat));
        });
    }

    @McpTool(name = "compare_revisions", generateOutputSchema = false,
            description = "Compares two versions of a page: from (history id) against to (history id) or against the "
                    + "current page when to is omitted. Returns word-level differences of name and text.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public McpModels.Comparison compareRevisions(
            @McpToolParam(description = "Page id", required = true) int page,
            @McpToolParam(description = "History id of the older version", required = true) int from,
            @McpToolParam(description = "History id of the newer version; omit for the current page", required = false) Integer to) {
        return support.read("compare_revisions", params("page", page, "from", from, "to", to), () -> {
            Page current = historyPage(page);
            PageRevision left = revision(page, from);
            PageRevision right = to == null ? new PageRevision(0, current.getRevision(), current.getName(), current.getContent(),
                    current.getChangeDate(), true, labels.listForPage(new PageId(page))) : revision(page, to);
            var names = RevisionDiff.compare(left.name(), right.name()).stream().map(part -> new McpModels.DiffPart(part.kind(), part.text())).toList();
            var texts = RevisionDiff.compare(RevisionDiff.text(left.content()), RevisionDiff.text(right.content())).stream()
                    .map(part -> new McpModels.DiffPart(part.kind(), part.text())).toList();
            boolean changed = names.stream().anyMatch(part -> !part.kind().equals("same")) || texts.stream().anyMatch(part -> !part.kind().equals("same"));
            return new McpModels.Comparison(McpModels.PageRef.of(current), "revision " + left.revision(),
                    to == null ? "current (revision " + current.getRevision() + ")" : "revision " + right.revision(), changed, names, texts);
        });
    }

    // --- helpers --------------------------------------------------------------------------------------

    /** History of readable pages; deleted pages only administrators see, like under Admin → Deleted pages. */
    private Page historyPage(int id) {
        try {
            Page page = pages.findById(new PageId(id));
            if (page == null) throw new McpToolException(McpPages.NOT_READABLE);
            boolean deleted = Boolean.TRUE.equals(page.getDeleted());
            if (deleted ? !McpToolSupport.user().isAdmin() : !access.canView(id)) throw new McpToolException(McpPages.NOT_READABLE);
            return page;
        } catch (EmptyResultDataAccessException e) {
            throw new McpToolException(McpPages.NOT_READABLE);
        }
    }

    private PageRevision revision(int page, int historyId) {
        try {
            return pages.findRevision(new PageId(page), historyId);
        } catch (EmptyResultDataAccessException e) {
            throw new McpToolException("The version was not found.");
        }
    }

    private File attachmentOf(int id) {
        try {
            File file = files.findById(id);
            if (file == null || Boolean.TRUE.equals(file.getDeleted())) throw new McpToolException("The attachment was not found.");
            return file;
        } catch (EmptyResultDataAccessException e) {
            throw new McpToolException("The attachment was not found.");
        }
    }

    private McpModels.Attachment attachment(File file) {
        return new McpModels.Attachment(file.getId(), file.getPageId(), file.getName(), file.getSize(), file.getType(),
                McpModels.time(file.getCreateDate()), access.origin() + "/api/v1/files/" + file.getId() + "/content");
    }

    /** text, html, pdf or null, judged by the stored type and the file name. */
    static String textKind(File file) {
        String type = file.getType() == null ? "" : file.getType().toLowerCase(Locale.ROOT);
        String name = file.getName() == null ? "" : file.getName().toLowerCase(Locale.ROOT);
        if (type.contains("pdf") || name.endsWith(".pdf")) return "pdf";
        if (type.contains("html") || name.endsWith(".html") || name.endsWith(".htm")) return "html";
        if (type.startsWith("text/") || type.contains("json") || type.contains("xml") || type.contains("csv")
                || name.matches(".*\\.(txt|md|markdown|csv|json|xml|yaml|yml|log|properties|ini|conf|java|kt|py|js|ts|sql|sh)$")) return "text";
        return null;
    }

    private static String pdfText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
