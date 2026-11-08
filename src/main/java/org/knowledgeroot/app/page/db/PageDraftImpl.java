package org.knowledgeroot.app.page.db;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.PageDraft;
import org.knowledgeroot.app.page.domain.PageDraftDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class PageDraftImpl implements PageDraftDao {
    /** Labels never contain line breaks (they come from a comma-separated field), so one per line is safe. */
    private static final String LABEL_SEPARATOR = "\n";
    private final JdbcClient jdbcClient;

    @Override
    public Optional<PageDraft> find(PageId pageId) {
        return jdbcClient.sql("""
                SELECT d.page_id, d.name, d.content, d.labels, d.base_revision, d.changed_by, u.login AS changed_by_login, d.change_date
                FROM page_draft d LEFT JOIN `user` u ON u.id = d.changed_by
                WHERE d.page_id = :page
                """).param("page", pageId.value())
                .query((rs, row) -> new PageDraft(new PageId(rs.getInt("page_id")), rs.getString("name"), rs.getString("content"),
                        labels(rs.getString("labels")), rs.getLong("base_revision"), rs.getObject("changed_by", Integer.class),
                        rs.getString("changed_by_login"), rs.getTimestamp("change_date").toLocalDateTime()))
                .optional();
    }

    @Override
    public void save(PageDraft draft) {
        jdbcClient.sql("""
                INSERT INTO page_draft (page_id, name, content, labels, base_revision, changed_by, change_date)
                VALUES (:page, :name, :content, :labels, :base, :by, :at)
                ON DUPLICATE KEY UPDATE name = VALUES(name), content = VALUES(content), labels = VALUES(labels),
                    changed_by = VALUES(changed_by), change_date = VALUES(change_date)
                """)
                .param("page", draft.pageId().value()).param("name", draft.name()).param("content", draft.content())
                .param("labels", String.join(LABEL_SEPARATOR, draft.labels())).param("base", draft.baseRevision())
                .param("by", draft.changedBy()).param("at", draft.changeDate())
                .update();
    }

    @Override
    public void delete(PageId pageId) {
        deleteKeepingLog(pageId);
        // The live-collaboration log (R63) describes the working copy and goes with it.
        jdbcClient.sql("DELETE FROM page_collab_update WHERE page_id = :page").param("page", pageId.value()).update();
    }

    @Override
    public void deleteKeepingLog(PageId pageId) {
        jdbcClient.sql("DELETE FROM page_draft WHERE page_id = :page").param("page", pageId.value()).update();
    }

    @Override
    public Set<Integer> pagesWithDraft(Collection<Integer> pageIds) {
        if (pageIds.isEmpty()) return Set.of();
        return new HashSet<>(jdbcClient.sql("SELECT page_id FROM page_draft WHERE page_id IN (:pages)")
                .param("pages", pageIds).query(Integer.class).list());
    }

    private static List<String> labels(String stored) {
        if (stored == null || stored.isEmpty()) return List.of();
        return Arrays.stream(stored.split(LABEL_SEPARATOR)).filter(label -> !label.isBlank()).toList();
    }
}
