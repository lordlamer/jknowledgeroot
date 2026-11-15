package org.knowledgeroot.app.file.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The searchable text of attachments ({@code file_text}, R68): written right after an upload and,
 * for attachments that exist without a row (older installations, uploads that failed to extract),
 * by a catch-up job in batches. Attachments without extractable text get an empty row so that the
 * job does not retry them forever.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentIndex {
    static final int BATCH = 50;
    private final JdbcClient jdbc;
    private final FileDao files;

    /** Extracts and stores the text of one attachment; failures are logged and leave an empty row. */
    public void index(int fileId) {
        File meta;
        try {
            meta = files.findById(fileId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return;
        }
        String text = "";
        if (AttachmentText.kind(meta) != null) {
            try (InputStream content = files.loadFile(fileId)) {
                String extracted = AttachmentText.extract(meta, content);
                if (extracted != null) text = extracted;
            } catch (Exception e) {
                log.warn("Attachment {} ({}) could not be read for the search index: {}", fileId, meta.getName(), e.getMessage());
            }
        }
        jdbc.sql("""
                INSERT INTO file_text (file_id, text, indexed_at) VALUES (:id, :text, :now)
                ON DUPLICATE KEY UPDATE text = VALUES(text), indexed_at = VALUES(indexed_at)
                """).param("id", fileId).param("text", text).param("now", LocalDateTime.now()).update();
    }

    /** The indexed text of an attachment, for search snippets. */
    public Optional<String> text(int fileId) {
        return jdbc.sql("SELECT text FROM file_text WHERE file_id = :id").param("id", fileId).query(String.class).optional();
    }

    /** Attachments still without a row, oldest first, one batch per run. Returns how many were indexed. */
    public int catchUp() {
        List<Integer> pending = jdbc.sql("""
                SELECT f.id FROM file f LEFT JOIN file_text t ON t.file_id = f.id
                WHERE f.deleted = 0 AND t.file_id IS NULL ORDER BY f.id LIMIT :limit
                """).param("limit", BATCH).query(Integer.class).list();
        for (int id : pending) index(id);
        if (!pending.isEmpty()) log.info("Indexed the text of {} attachments for the search", pending.size());
        return pending.size();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        try {
            while (catchUp() == BATCH) { /* keep going until the backlog is done */ }
        } catch (RuntimeException e) {
            log.warn("Attachment search index catch-up stopped: {}", e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void sweep() {
        try {
            catchUp();
        } catch (RuntimeException e) {
            log.warn("Attachment search index catch-up stopped: {}", e.getMessage());
        }
    }
}
