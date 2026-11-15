package org.knowledgeroot.app.file.domain;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Text of an attachment for the search index (R68) and the MCP tools: text-like files, HTML and PDF. */
public final class AttachmentText {
    /** Attachments larger than this are not read into memory for text extraction. */
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    /** Characters kept in the index per attachment. */
    public static final int MAX_CHARS = 1_000_000;

    private AttachmentText() {}

    /** text, html, pdf or null, judged by the stored type and the file name. */
    public static String kind(String type, String name) {
        String lowerType = type == null ? "" : type.toLowerCase(Locale.ROOT);
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lowerType.contains("pdf") || lowerName.endsWith(".pdf")) return "pdf";
        if (lowerType.contains("html") || lowerName.endsWith(".html") || lowerName.endsWith(".htm")) return "html";
        if (lowerType.startsWith("text/") || lowerType.contains("json") || lowerType.contains("xml") || lowerType.contains("csv")
                || lowerName.matches(".*\\.(txt|md|markdown|csv|json|xml|yaml|yml|log|properties|ini|conf|java|kt|py|js|ts|sql|sh)$")) return "text";
        return null;
    }

    public static String kind(File file) {
        return kind(file.getType(), file.getName());
    }

    /**
     * The plain text of the attachment, or null when the type has none or the file is too large.
     * HTML loses its tags, PDF is read with PDFBox; the result is bounded to {@link #MAX_CHARS}.
     */
    public static String extract(File file, InputStream content) throws IOException {
        String kind = kind(file);
        if (kind == null || content == null) return null;
        if (file.getSize() != null && file.getSize() > MAX_BYTES) return null;
        byte[] bytes = content.readNBytes((int) MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) return null;
        String text = switch (kind) {
            case "pdf" -> pdfText(bytes);
            case "html" -> HtmlUtils.htmlUnescape(new String(bytes, StandardCharsets.UTF_8)
                    .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ").replaceAll("<[^>]*>", " "));
            default -> new String(bytes, StandardCharsets.UTF_8);
        };
        text = text.replace('\u0000', ' ').replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    public static String pdfText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
