-- The baseline created these tables with the 3-byte "utf8" charset, which rejects characters outside
-- the Basic Multilingual Plane (for example emoji typed on phone keyboards) with an SQL error.
-- Converting keeps every value; tables created by later changes already use utf8mb4.
ALTER TABLE `user` CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE `group` CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE group_member CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE acl CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE page CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE page_permission CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE page_history CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE page_comment CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE tag CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE tag_content CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE user_page_star CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE file CONVERT TO CHARACTER SET utf8mb4;
