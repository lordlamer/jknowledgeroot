package org.knowledgeroot.app.page.domain;

import java.util.List;

public interface PageLabelDao {
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
