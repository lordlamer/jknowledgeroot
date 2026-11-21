package org.knowledgeroot.app.page.export;

import org.jsoup.nodes.Element;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the writers need about one page: the metadata lines and the sanitised content as a jsoup
 * body whose {@code img} elements carry {@code data-image="<index>"} when the attachment could be loaded
 * for the viewer. Other images stay as they are; the PDF and Word writers replace them with a note, the
 * Markdown writer keeps their address.
 */
public record ExportDocument(String title, String space, List<String> path, List<String> labels,
                             String createdBy, LocalDateTime createDate, String changedBy, LocalDateTime changeDate,
                             Element body, List<ExportImage> images) {

    public static final String IMAGE_ATTRIBUTE = "data-image";

    /** The embedded image an {@code img} element refers to, or null. */
    public ExportImage image(Element img) {
        String index = img.attr(IMAGE_ATTRIBUTE);
        if (index.isEmpty()) return null;
        try {
            int i = Integer.parseInt(index);
            return i >= 0 && i < images.size() ? images.get(i) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** What to print instead of an image that is not embedded: its alternative text, else its address. */
    public static String describe(Element img) {
        String alt = img.attr("alt").trim();
        return alt.isEmpty() ? img.attr("src").trim() : alt;
    }
}
