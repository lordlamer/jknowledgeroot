package org.knowledgeroot.app.page.domain;

import java.util.List;
import java.util.Optional;

public interface PageCommentDao {
    /**
     * List comments for a page, oldest first.
     */
    List<PageComment> listForPage(PageId pageId);

    /**
     * Count comments on a page.
     */
    int countForPage(PageId pageId);

    /**
     * Create a new comment. Returns the generated id.
     */
    Integer create(PageId pageId, Integer userId, String content);

    /**
     * Find a comment by id.
     */
    Optional<PageComment> findById(Integer commentId);

    /**
     * Delete a comment by id.
     */
    void delete(Integer commentId);
}
