package org.knowledgeroot.app.page.domain;

/** A page was moved (R66): below another parent, or to the top level of a space ({@code toParent} 0). */
public record PageMoved(PageId pageId, Integer actorId, int fromParent, int toParent, int fromSpace, int toSpace) {
}
