package org.knowledgeroot.app.page.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Working copies (R62): one per page, written by autosave, removed by publishing or discarding. */
public interface PageDraftDao {
    Optional<PageDraft> find(PageId pageId);

    /** Inserts or replaces the working copy; an existing copy keeps its base revision. */
    void save(PageDraft draft);

    void delete(PageId pageId);

    /** Those of the given pages that currently have a working copy. */
    Set<Integer> pagesWithDraft(Collection<Integer> pageIds);
}
