-- Serialize structural moves across application instances to prevent concurrent cycles.
CREATE TABLE page_tree_lock (id INT NOT NULL PRIMARY KEY);
INSERT INTO page_tree_lock (id) VALUES (1);
