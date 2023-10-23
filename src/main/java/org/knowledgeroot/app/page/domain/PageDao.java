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
}
