package org.knowledgeroot.domain;

import org.knowledgeroot.app.api.filter.PageFilter;
import org.knowledgeroot.persistence.Page.Page;

import java.util.List;

public interface PageDao {
    /**
     * find pages
     * @return
     */
    List<Page> listPages(PageFilter pageFilter);

    /**
     * find page by given id
     * @param id
     * @return
     */
    Page findById(Integer id);

    /**
     * check if page exists
     * @param page
     * @return
     */
    boolean isPageExist(Page page);

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
     * @param id
     */
    void deletePageById(Integer id);
}
