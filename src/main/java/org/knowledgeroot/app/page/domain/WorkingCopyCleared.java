package org.knowledgeroot.app.page.domain;

/** Published when a page's working copy was published or discarded, so open live editors reload (R63). */
public record WorkingCopyCleared(PageId pageId) {}
