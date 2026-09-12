-- Keep existing MD5 keys untouched; new uploads use sha256- followed by 64 hexadecimal characters.
ALTER TABLE file MODIFY COLUMN hash VARCHAR(71) NOT NULL DEFAULT '';
ALTER TABLE file MODIFY COLUMN name VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL DEFAULT '';
