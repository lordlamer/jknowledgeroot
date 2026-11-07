-- Working copies and draft pages (R62). A page keeps its published version in `page`; the editor
-- saves into `page_draft` automatically, and "Publish" moves the working copy into `page` with a
-- history snapshot. A page that was never published (published = 0) is a draft: only users who
-- may edit it can see it.
ALTER TABLE page ADD COLUMN published BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE page_draft (
    page_id INT NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    labels VARCHAR(4096) NOT NULL DEFAULT '',
    base_revision BIGINT NOT NULL,
    changed_by INT NULL,
    change_date DATETIME(3) NOT NULL,
    CONSTRAINT page_draft_page FOREIGN KEY (page_id) REFERENCES page(id) ON DELETE CASCADE,
    CONSTRAINT page_draft_user FOREIGN KEY (changed_by) REFERENCES `user`(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
