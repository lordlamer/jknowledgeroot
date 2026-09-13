ALTER TABLE page ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE page_history MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE page_history MODIFY COLUMN created_by INTEGER NULL;
ALTER TABLE page_history MODIFY COLUMN changed_by INTEGER NULL;
-- Preserve any historical rows, including installations with duplicate legacy version numbers.
UPDATE page p SET revision = COALESCE((SELECT MAX(h.version) + 1 FROM page_history h WHERE h.page_id=p.id), 0);
CREATE INDEX idx_page_history_page_id ON page_history (page_id, id);
CREATE TABLE page_history_label (
    history_id INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (history_id, name),
    FOREIGN KEY (history_id) REFERENCES page_history(id) ON DELETE CASCADE
);
-- Legacy snapshots have no captured labels; do not pretend their empty label list is known.
ALTER TABLE page_history ADD COLUMN labels_captured BOOLEAN NOT NULL DEFAULT FALSE;
-- Already deleted legacy pages also need a recoverable snapshot.
INSERT INTO page_history (page_id,version,parent,name,content,time_start,time_end,
    created_by,create_date,changed_by,change_date,active,deleted,labels_captured)
SELECT id,revision,parent,name,content,time_start,time_end,created_by,create_date,
    changed_by,change_date,active,deleted,TRUE FROM page WHERE deleted=TRUE;
INSERT INTO page_history_label (history_id,name)
SELECT DISTINCT h.id,t.name FROM page p JOIN page_history h ON h.page_id=p.id AND h.version=p.revision
    JOIN tag_content tc ON tc.page_id=p.id JOIN tag t ON t.id=tc.tag_id WHERE p.deleted=TRUE;
UPDATE page SET revision=revision+1 WHERE deleted=TRUE;
