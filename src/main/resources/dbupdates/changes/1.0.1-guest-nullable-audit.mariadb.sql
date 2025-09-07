-- Allow NULLs for audit columns to support guest-created content
-- MariaDB DDL
ALTER TABLE page 
    MODIFY COLUMN created_by INTEGER NULL,
    MODIFY COLUMN changed_by INTEGER NULL;

ALTER TABLE page_permission 
    MODIFY COLUMN created_by INTEGER NULL,
    MODIFY COLUMN changed_by INTEGER NULL;

ALTER TABLE page_history 
    MODIFY COLUMN created_by INTEGER NULL,
    MODIFY COLUMN changed_by INTEGER NULL;

ALTER TABLE file 
    MODIFY COLUMN created_by INTEGER NULL,
    MODIFY COLUMN changed_by INTEGER NULL;
