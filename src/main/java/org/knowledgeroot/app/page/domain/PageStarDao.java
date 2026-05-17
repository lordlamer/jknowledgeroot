package org.knowledgeroot.app.page.domain;

import java.util.List;

public interface PageStarDao {
    /**
     * Star a page for a user. Idempotent — no-op if already starred.
     */
    void starPage(Integer userId, PageId pageId);

    /**
     * Remove the star from a page for a user. Idempotent.
     */
    void unstarPage(Integer userId, PageId pageId);

    /**
     * Whether the page is starred by the given user.
     */
    boolean isStarred(Integer userId, PageId pageId);

    /**
     * List pages the user starred. Ordered by most recently starred first.
     * Only pages the user still has VIEW permission on are returned.
     */
    List<Page> listStarredPages(Integer userId);
}
