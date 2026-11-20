package org.knowledgeroot.app.page.export;

import org.springframework.context.MessageSource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The translated words of an export (headings of the metadata lines, date format, notes). */
public final class ExportText {
    private final MessageSource messages;
    private final Locale locale;
    private final DateTimeFormatter dates;

    public ExportText(MessageSource messages, Locale locale) {
        this.messages = messages;
        this.locale = locale;
        this.dates = DateTimeFormatter.ofPattern(message("format.datetime"), locale);
    }

    public Locale locale() {
        return locale;
    }

    String message(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    public String date(LocalDateTime value) {
        return value == null ? "" : dates.format(value);
    }

    /** The note printed where an image is not part of the export. */
    public String imageOmitted(String what) {
        return message("export.image-omitted", what);
    }

    /** Space, path, labels and the two author lines, in this order, without empty entries. */
    public List<String> metaLines(ExportDocument document) {
        List<String> lines = new ArrayList<>();
        if (document.space() != null && !document.space().isBlank()) lines.add(message("export.space") + ": " + document.space());
        if (!document.path().isEmpty()) lines.add(message("export.path") + ": " + String.join(" › ", document.path()));
        if (!document.labels().isEmpty()) lines.add(message("export.labels") + ": " + String.join(", ", document.labels()));
        if (document.createDate() != null) lines.add(author("page.show.created-by", document.createdBy(), document.createDate()));
        if (document.changeDate() != null) lines.add(author("page.show.changed-by", document.changedBy(), document.changeDate()));
        return lines;
    }

    private String author(String key, String login, LocalDateTime when) {
        String who = login == null || login.isBlank() ? message("page.show.guest") : login;
        return message(key) + " " + who + " " + message("page.show.at") + " " + date(when);
    }
}
