package org.knowledgeroot.domain;

import org.knowledgeroot.app.api.filter.ContentFilter;
import org.knowledgeroot.persistence.Content.Content;

import java.util.List;

public interface ContentDao {
    /**
     * find all contents
     * @return
     */
    List<Content> listContents(ContentFilter contentFilter);

    /**
     * find content by given id
     * @param id
     * @return
     */
    Content findById(Integer id);

    /**
     * check if content exists
     * @param content
     * @return
     */
    boolean isContentExist(Content content);

    /**
     * create content from domain object
     * @param content
     */
    void createContent(Content content);

    /**
     * update existing content
     * @param content
     */
    void updateContent(Content content);

    /**
     * delete all contents
     */
    void deleteAllContents();

    /**
     * delete content by given id
     * @param id
     */
    void deleteContentById(Integer id);
}
