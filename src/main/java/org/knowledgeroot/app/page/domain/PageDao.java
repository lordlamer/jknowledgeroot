package org.knowledgeroot.app.page.domain;

import java.util.List;

public interface PageDao {
    /**
     * find pages
     * @return
     */
    List<Page> listPages(PageFilter pageFilter);

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
    void createPage(Page page);

    /**
     * update existing page
     * @param page
     */
    void updatePage(Page page);

    /**
     * delete all pages
     */
    void deleteAllPages();

    /**
     * delete page by given id
     * @param pageId
     */
    void deletePageById(PageId pageId);

    /**
     * search for content on pages
     * @param searchQuery
     * @return
     */
    List<Page> searchContent(String searchQuery);

    /**
     * Get page hierarchy from root to current page for breadcrumb navigation
     * @param pageId ID of the current page
     * @return List of pages ordered from root to current page
     */
    List<Page> getPageHierarchy(PageId pageId);
}
