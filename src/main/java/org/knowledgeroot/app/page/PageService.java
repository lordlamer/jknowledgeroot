package org.knowledgeroot.app.page;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PageService {
    private final PageDao pageImpl;

    /**
     * find all pages
     * @return
     */
    public List<Page> listPages(PageFilter pageFilter) {
        return pageImpl.listPages(pageFilter);
    }

    /**
     * find page by given id
     * @param id
     * @return
     */
    public Page findById(long id) {
        return pageImpl.findById(id);
    }

    /**
     * check if page exists
     * @param page
     * @return
     */
    public boolean isPageExist(Page page) {
        return pageImpl.isPageExist(page);
    }

    /**
     * create page
     * @param page
     */
    public void createPage(Page page) {
        pageImpl.createPage(page);
    }

    /**
     * update existing page
     * @param page
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
     * @param id
     */
    public void deletePageById(long id) {
        pageImpl.deletePageById(id);
    }
}
