package org.knowledgeroot.app.page.domain;

/**
 * Published when a working copy that came from the live document was published (R63): the live log
 * stays, open editors learn the new revision and keep editing. {@code login} is the publisher, may be null.
 */
public record WorkingCopyPublished(PageId pageId, long revision, String login) {}
