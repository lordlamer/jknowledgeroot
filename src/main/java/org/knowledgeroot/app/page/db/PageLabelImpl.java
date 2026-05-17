package org.knowledgeroot.app.page.db;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PageLabelDao;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class PageLabelImpl implements PageLabelDao {
    private static final int MAX_LABEL_LENGTH = 64;
    private static final int MAX_LABELS_PER_PAGE = 32;

    private final JdbcClient jdbcClient;

    @Override
    public List<String> listForPage(PageId pageId) {
        return jdbcClient.sql("""
                SELECT t.name
                FROM tag_content tc
                JOIN tag t ON t.id = tc.tag_id
                WHERE tc.page_id = :pageId
                ORDER BY t.name ASC
                """)
                .param("pageId", pageId.value())
                .query(String.class)
                .list();
    }

    @Override
    public void setForPage(PageId pageId, List<String> labels) {
        // Normalise: trim, drop empty, truncate, de-duplicate case-insensitively
        // while preserving the first-seen casing.
        LinkedHashMap<String, String> cleaned = new LinkedHashMap<>();
        if (labels != null) {
            for (String raw : labels) {
                if (raw == null) continue;
                String trimmed = raw.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.length() > MAX_LABEL_LENGTH) {
                    trimmed = trimmed.substring(0, MAX_LABEL_LENGTH);
                }
                cleaned.putIfAbsent(trimmed.toLowerCase(), trimmed);
                if (cleaned.size() >= MAX_LABELS_PER_PAGE) break;
            }
        }

        clearForPage(pageId);
        for (String name : cleaned.values()) {
            int tagId = findOrCreateTag(name);
            jdbcClient.sql("INSERT INTO tag_content (tag_id, page_id) VALUES (:tagId, :pageId)")
                    .param("tagId", tagId)
                    .param("pageId", pageId.value())
                    .update();
        }
    }

    @Override
    public void clearForPage(PageId pageId) {
        jdbcClient.sql("DELETE FROM tag_content WHERE page_id = :pageId")
                .param("pageId", pageId.value())
                .update();
    }

    private int findOrCreateTag(String name) {
        Optional<Integer> existing = jdbcClient.sql("SELECT id FROM tag WHERE name = :name")
                .param("name", name)
                .query(Integer.class)
                .optional();
        if (existing.isPresent()) {
            return existing.get();
        }

        KeyHolder kh = new GeneratedKeyHolder();
        try {
            jdbcClient.sql("INSERT INTO tag (name) VALUES (:name)")
                    .param("name", name)
                    .update(kh);
            Number key = kh.getKey();
            if (key != null) {
                return key.intValue();
            }
        } catch (DuplicateKeyException e) {
            // Another transaction created the same tag concurrently — re-read below.
        }

        return jdbcClient.sql("SELECT id FROM tag WHERE name = :name")
                .param("name", name)
                .query(Integer.class)
                .single();
    }
}
