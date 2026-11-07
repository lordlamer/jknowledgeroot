package org.knowledgeroot.app.page.domain;

import java.util.List;

/**
 * The Yjs update log of a page's working copy (R63). The server stores and relays the bytes
 * without interpreting them; browsers merge them. {@link PageDraftDao#delete(PageId)} clears the
 * log together with the working copy.
 */
public interface PageCollabStore {
    record Update(long id, byte[] bytes) {}

    /** All stored updates of the page in insertion order. */
    List<Update> updates(PageId pageId);

    /** Appends an update and returns its id. */
    long append(PageId pageId, byte[] bytes);

    /**
     * Replaces every update up to and including {@code upToId} with one snapshot that already
     * contains them. Updates appended after {@code upToId} stay.
     */
    void compact(PageId pageId, byte[] snapshot, long upToId);

    /** Bytes currently stored for the page. */
    long size(PageId pageId);

    void clear(PageId pageId);
}
