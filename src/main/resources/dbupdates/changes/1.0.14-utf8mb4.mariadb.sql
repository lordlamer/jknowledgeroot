-- The baseline created these tables with the 3-byte "utf8" charset, which rejects characters outside
-- the Basic Multilingual Plane (for example emoji typed on phone keyboards) with an SQL error.
-- Converting keeps every value; tables created by later changes already use utf8mb4.
-- The collation is named explicitly so the result does not depend on the server's
-- character_set_collations setting: utf8mb4_uca1400_ai_ci is what MariaDB 12.3 gives new
-- installations anyway. Installations created with utf8_general_ci get Unicode comparison
-- rules from here on (for example "ss" = "ß"); duplicate unique values under these rules
-- make the conversion fail, see the upgrade notes in docs/installation.md.
ALTER TABLE `user` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE `group` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE group_member CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE acl CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE page CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE page_permission CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE page_history CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE page_comment CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE tag CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE tag_content CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE user_page_star CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
ALTER TABLE file CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;
