-- Spaces: containers around page trees with their own permissions. A viewer without view
-- permission on a space cannot see any of its pages, whatever the page permissions say.
CREATE TABLE space (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    space_key VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    home_page_id INT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_by INT NULL,
    create_date DATETIME(3) NOT NULL,
    changed_by INT NULL,
    change_date DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 'users' means every signed-in user; 'admin' may manage the space, 'edit' may create root pages.
CREATE TABLE space_permission (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    space_id INT NOT NULL,
    role_type ENUM('user', 'group', 'guest', 'users') NOT NULL,
    role_id INT NULL,
    permission_level ENUM('view', 'edit', 'admin') NOT NULL,
    created_by INT NULL,
    create_date DATETIME(3) NOT NULL,
    changed_by INT NULL,
    change_date DATETIME(3) NOT NULL,
    INDEX space_permission_role (space_id, role_type, role_id),
    FOREIGN KEY (space_id) REFERENCES space(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Application-wide grants independent of the administrator role; first use: 'create-space'.
CREATE TABLE global_permission (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    permission VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role_type ENUM('user', 'group', 'users') NOT NULL,
    role_id INT NULL,
    created_by INT NULL,
    create_date DATETIME(3) NOT NULL,
    UNIQUE KEY global_permission_role (permission, role_type, role_id)
) ENGINE=InnoDB;

-- The default space takes every existing page; guests may read it and signed-in users may
-- create root pages in it, which matches the behaviour before spaces existed.
INSERT INTO space (space_key, name, description, create_date, change_date)
VALUES ('KR', 'Knowledgeroot', '', NOW(3), NOW(3));
INSERT INTO space_permission (space_id, role_type, role_id, permission_level, create_date, change_date)
SELECT id, 'guest', NULL, 'view', NOW(3), NOW(3) FROM space WHERE space_key = 'KR';
INSERT INTO space_permission (space_id, role_type, role_id, permission_level, create_date, change_date)
SELECT id, 'users', NULL, 'edit', NOW(3), NOW(3) FROM space WHERE space_key = 'KR';

-- The default keeps raw inserts (demo data, REST API clients) in the default space.
ALTER TABLE page ADD COLUMN space_id INT NOT NULL DEFAULT 1;
UPDATE page SET space_id = (SELECT id FROM space WHERE space_key = 'KR');
ALTER TABLE page ADD INDEX page_space_parent (space_id, parent, deleted);
ALTER TABLE page ADD CONSTRAINT page_space FOREIGN KEY (space_id) REFERENCES space(id) ON DELETE RESTRICT;
ALTER TABLE space ADD CONSTRAINT space_home_page FOREIGN KEY (home_page_id) REFERENCES page(id) ON DELETE SET NULL;
