package org.knowledgeroot.app.content.domain;

import java.util.List;

public interface ContentDao {
    /**
     * find all contents
     * @return
     */
    List<Content> listContents(ContentFilter contentFilter);

    /**
     * find content by given id
     * @param contentId
     * @return
     */
    Content findById(ContentId contentId);

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
     * @param contentId
     */
    void deleteContentById(ContentId contentId);

    /**
     * search for content by given query
     * @param searchQuery
     * @return
     */
    List<Content> searchContent(String searchQuery);
}
