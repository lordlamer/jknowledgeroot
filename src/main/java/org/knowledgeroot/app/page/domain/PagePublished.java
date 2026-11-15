package org.knowledgeroot.app.page.domain;

/** A new version of a page became visible to readers (R66): edit, publish, or a page created as published. */
public record PagePublished(PageId pageId, Integer actorId) {
}
