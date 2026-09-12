-- Preserve all existing explicit grants. New children opt in during creation.
ALTER TABLE page ADD COLUMN inherit_permissions BOOLEAN NOT NULL DEFAULT FALSE;
