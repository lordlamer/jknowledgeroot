package org.knowledgeroot.app.page.domain;

/** A comment was added to a page (R66); {@code text} is the comment as stored. */
public record PageCommented(PageId pageId, int commentId, Integer actorId, String text) {
}
