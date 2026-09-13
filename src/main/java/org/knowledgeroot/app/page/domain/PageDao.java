package org.knowledgeroot.app.page.domain;

import java.util.List;

public interface PageDao {
    /**
     * find pages
     * @return
     */
    List<Page> listPages(PageFilter pageFilter);

    /** Visible results, including parent redaction, with permissions applied before LIMIT. */
    List<Page> listVisiblePages(PageFilter pageFilter, Integer viewer);

    /**
     * find page by given id
     * @param pageId
     * @return
     */
    Page findById(PageId pageId);

    /**
     * create page from domain object
     * @param page
     */
    int createPage(Page page);

    /**
     * update existing page
     * @param page
     */
    void updatePage(Page page);

    /** Saved versions, newest first; at most 51 entries for pagination. */
    List<PageRevision> listRevisions(PageId id, int start);
    PageRevision findRevision(PageId id, int historyId);


    /**
     * Get page hierarchy from root to current page for breadcrumb navigation
     * @param pageId ID of the current page
     * @return List of pages ordered from root to current page
     */
    List<Page> getPageHierarchy(PageId pageId);
}
