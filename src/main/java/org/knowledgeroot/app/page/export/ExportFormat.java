package org.knowledgeroot.app.page.export;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/** The download formats of a page (R69). */
public enum ExportFormat {
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    MARKDOWN("md", "text/markdown;charset=UTF-8");

    private final String extension;
    private final String mediaType;

    ExportFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    /** The {@code format} request parameter: pdf, docx (or word), markdown (or md). */
    public static ExportFormat parse(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "pdf" -> PDF;
            case "docx", "word" -> DOCX;
            case "markdown", "md" -> MARKDOWN;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose pdf, docx or markdown.");
        };
    }
}
