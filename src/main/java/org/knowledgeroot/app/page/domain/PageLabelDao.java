package org.knowledgeroot.app.page.domain;

import java.util.List;

public interface PageLabelDao {
    /** A label and the number of pages carrying it. */
    record LabelCount(String name, int pages) {}

    /**
     * Labels in use on pages the viewer may read, alphabetically, optionally only in one space.
     */
    List<LabelCount> listLabels(Integer spaceId, Integer viewer);

    /**
     * Labels attached to a page, alphabetically sorted.
     */
    List<String> listForPage(PageId pageId);

    /**
     * Replace the full set of labels attached to a page. Labels are
     * trimmed and de-duplicated case-insensitively before persisting.
     * Existing tags are reused via tag.name; new ones are created.
     */
    void setForPage(PageId pageId, List<String> labels);

    /**
     * Remove all labels from a page (used on delete).
     */
    void clearForPage(PageId pageId);
}
