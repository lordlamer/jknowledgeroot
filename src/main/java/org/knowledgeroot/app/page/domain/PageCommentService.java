package org.knowledgeroot.app.page.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates comments for the web UI, the client API and MCP alike and announces them for notifications (R66). */
@Service
@RequiredArgsConstructor
public class PageCommentService {
    private final PageCommentDao comments;
    private final ApplicationEventPublisher events;

    /** Stores the comment as given (callers trim and bound it) and returns its id. */
    @Transactional
    public int create(PageId pageId, Integer userId, String text) {
        int id = comments.create(pageId, userId, text);
        events.publishEvent(new PageCommented(pageId, id, userId, text));
        return id;
    }
}
