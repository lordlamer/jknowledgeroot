package org.knowledgeroot.app.page.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The working copy of a page: what the editor saved automatically since the last publication.
 * There is one per page, shared by everyone who may edit it. {@code baseRevision} is the page
 * revision the working copy started from; a smaller value than the page's current revision means
 * the page was published from elsewhere meanwhile ({@link #isStale(long)}).
 */
public record PageDraft(PageId pageId, String name, String content, List<String> labels, long baseRevision,
                        Integer changedBy, String changedByLogin, LocalDateTime changeDate) {
    public boolean isStale(long currentRevision) {
        return baseRevision < currentRevision;
    }
}
