package org.knowledgeroot.app.page;

import lombok.AllArgsConstructor;
import org.knowledgeroot.app.page.impl.database.Page;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PageService {
    private final PageDao pageImpl;

    /**
     * find all pages
     */
    public List<Page> listPages(PageFilter pageFilter) {
        return pageImpl.listPages(pageFilter);
    }

    /**
     * find page by given id
     * @param id page id
     */
    public Page findById(long id) {
        return pageImpl.findById(id);
    }

    /**
     * check if page exists
     * @param page page to check
     */
    public boolean isPageExist(Page page) {
        return pageImpl.isPageExist(page);
    }

    /**
     * create page
     * @param page page to create
     */
    public void createPage(Page page) {
        pageImpl.createPage(page);
    }

    /**
     * update existing page
     * @param page page to update
     */
    public void updatePage(Page page) {
        pageImpl.updatePage(page);
    }

    /**
     * delete all pages
     */
    public void deleteAllPages() {
        pageImpl.deleteAllPages();
    }

    /**
     * delete page by id
     * @param id page to delete
     */
    public void deletePageById(long id) {
        pageImpl.deletePageById(id);
    }
}
