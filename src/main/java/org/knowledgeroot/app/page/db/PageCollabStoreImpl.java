package org.knowledgeroot.app.page.db;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.PageCollabStore;
import org.knowledgeroot.app.page.domain.PageId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class PageCollabStoreImpl implements PageCollabStore {
    private final JdbcClient jdbcClient;

    @Override
    public List<Update> updates(PageId pageId) {
        return jdbcClient.sql("SELECT id, update_bytes FROM page_collab_update WHERE page_id = :page ORDER BY id")
                .param("page", pageId.value())
                .query((rs, row) -> new Update(rs.getLong("id"), rs.getBytes("update_bytes"))).list();
    }

    @Override
    public long append(PageId pageId, byte[] bytes) {
        var key = new GeneratedKeyHolder();
        jdbcClient.sql("INSERT INTO page_collab_update (page_id, update_bytes, create_date) VALUES (:page, :bytes, :at)")
                .param("page", pageId.value()).param("bytes", bytes).param("at", LocalDateTime.now())
                .update(key);
        return key.getKey().longValue();
    }

    @Override
    public void compact(PageId pageId, byte[] snapshot, long upToId) {
        // The snapshot takes the place of the updates it contains; later updates keep their order after it
        // because the snapshot row is inserted first and ids only grow.
        jdbcClient.sql("DELETE FROM page_collab_update WHERE page_id = :page AND id <= :upTo")
                .param("page", pageId.value()).param("upTo", upToId).update();
        // Re-insert with an id below the remaining updates is impossible with AUTO_INCREMENT, so the snapshot
        // is appended and the remaining later updates are re-appended behind it in their original order.
        var later = jdbcClient.sql("SELECT id, update_bytes FROM page_collab_update WHERE page_id = :page ORDER BY id")
                .param("page", pageId.value())
                .query((rs, row) -> new Update(rs.getLong("id"), rs.getBytes("update_bytes"))).list();
        jdbcClient.sql("DELETE FROM page_collab_update WHERE page_id = :page").param("page", pageId.value()).update();
        append(pageId, snapshot);
        for (Update update : later) append(pageId, update.bytes());
    }

    @Override
    public long size(PageId pageId) {
        return jdbcClient.sql("SELECT COALESCE(SUM(LENGTH(update_bytes)), 0) FROM page_collab_update WHERE page_id = :page")
                .param("page", pageId.value()).query(Long.class).single();
    }

    @Override
    public void clear(PageId pageId) {
        jdbcClient.sql("DELETE FROM page_collab_update WHERE page_id = :page").param("page", pageId.value()).update();
    }
}
