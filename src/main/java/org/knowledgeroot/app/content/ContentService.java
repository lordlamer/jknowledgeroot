package org.knowledgeroot.app.content;

import lombok.AllArgsConstructor;
import org.knowledgeroot.app.content.impl.database.Content;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ContentService {
    private final ContentDao contentImpl;

    /**
     * find all contents
     * @return
     */
    public List<Content> listContents(ContentFilter contentFilter) {
        return contentImpl.listContents(contentFilter);
    }

    /**
     * find content by given id
     * @param id
     * @return
     */
    public Content findById(long id) {
        return contentImpl.findById(id);
    }

    /**
     * check if content exists
     * @param content
     * @return
     */
    public boolean isContentExist(Content content) {
        return contentImpl.isContentExist(content);
    }

    /**
     * create content
     * @param content
     */
    public void createContent(Content content) {
        contentImpl.createContent(content);
    }

    /**
     * update existing content
     * @param content
     */
    public void updateContent(Content content) {
        contentImpl.updateContent(content);
    }

    /**
     * delete all contents
     */
    public void deleteAllContents() {
        contentImpl.deleteAllContents();
    }

    /**
     * delete content by id
     * @param id
     */
    public void deleteContentById(long id) {
        contentImpl.deleteContentById(id);
    }
}
