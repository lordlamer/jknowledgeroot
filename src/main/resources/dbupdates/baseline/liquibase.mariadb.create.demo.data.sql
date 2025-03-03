-- Dump for Postgresql with demo data

-- user
INSERT INTO `user` (first_name, last_name, login, email, password, admin, language, timezone, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ('knowledgeroot', 'admin', 'admin', 'admin@localhost', '$2$1000$E556111996CF452E$B3EB80BC13DED8B02A2F09E505AED75737D4AF45D9A599623D39CD518771C306', true, 'en_US', 'Europe/Berlin', true, 0, '2012-10-01 22:00:00', 0, '2012-10-01 22:00:00', false);

INSERT INTO `user` (first_name, last_name, login, email, password, admin, language, timezone, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ('knowledgeroot', 'user', 'user', 'user@localhost', '$2$1000$E556111996CF452E$B3EB80BC13DED8B02A2F09E505AED75737D4AF45D9A599623D39CD518771C306', false, 'en_US', 'Europe/Berlin', true, 0, '2012-10-01 22:00:00', 0, '2012-10-01 22:00:00', false);

-- group
INSERT INTO `group` (name, description, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ('admin', 'administrators', true, 0, '2012-10-01 22:00:00', 0, '2012-10-01 22:00:00', false);

INSERT INTO `group` (name, description, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ('users', 'users', true, 0, '2012-10-01 22:00:00', 0, '2012-10-01 22:00:00', false);

INSERT INTO `group` (name, description, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ('others', 'others', true, 0, '2012-10-01 22:00:00', 0, '2012-10-01 22:00:00', false);

-- group members
INSERT INTO group_member (group_id, member_id, member_type)
VALUES ((SELECT id FROM `group` where name='admin'), (SELECT id FROM `user` where login='admin'), 'user');

-- pages
INSERT INTO page (parent, name, content, active, created_by, create_date, changed_by, change_date, deleted)
VALUES (0, 'first', 'first content', true, (SELECT id FROM `user` where login='admin'), '2012-10-01 22:00:00', (SELECT id FROM `user` where login='admin'), '2012-10-01 22:00:00', false);

INSERT INTO page (parent, name, content, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ((SELECT x.id FROM (SELECT * FROM page) x where x.name='first'), 'second', 'second content',true, (SELECT x.id FROM (SELECT * FROM `user`) x where x.login='admin'), '2012-10-01 22:00:00', (SELECT x.id FROM (SELECT * FROM `user`) x where x.login='admin'), '2012-10-01 22:00:00', false);

INSERT INTO page (parent, name, content, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ((SELECT x.id FROM (SELECT * FROM page) x where x.name='second'), 'third', 'third content',true, (SELECT x.id FROM (SELECT * FROM `user`) x where x.login='admin'), '2012-10-01 22:00:00', (SELECT x.id FROM (SELECT * FROM `user`) x where x.login='admin'), '2012-10-01 22:00:00', false);

-- files

-- acl
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageUsers', 'new', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageUsers', 'edit', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageUsers', 'delete', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageUsers', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageGroups', 'new', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageGroups', 'edit', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageGroups', 'delete', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageGroups', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageSystemPermissions', 'new', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageSystemPermissions', 'edit', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageSystemPermissions', 'delete', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageSystemPermissions', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageRootPages', 'new', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageRootPages', 'edit', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageRootPages', 'delete', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'manageRootPages', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_1', 'new', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_1', 'edit', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_1', 'delete', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_1', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_1', 'permission', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_1', 'new_content', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_2', 'new', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_2', 'edit', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_2', 'delete', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_2', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_2', 'permission', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_2', 'new_content', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_3', 'new', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_3', 'edit', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_3', 'delete', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_3', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_3', 'permission', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'page_3', 'new_content', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_0', 'page_1', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_0', 'page_1', 'new_content', 'allow');


-- Berechtigungen für Admin-Benutzer (ID 1) auf Seite "first" (ID 1)
INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date)
VALUES (1, 'user', 1, 'edit', 1, NOW(), 1, NOW());

-- Berechtigungen für Admin-Benutzer (ID 1) auf Seite "second" (ID 2)
INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date)
VALUES (2, 'user', 1, 'edit', 1, NOW(), 1, NOW());

-- Berechtigungen für Admin-Benutzer (ID 1) auf Seite "third" (ID 3)
INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date)
VALUES (3, 'user', 1, 'edit', 1, NOW(), 1, NOW());

-- Berechtigungen für Admin-Gruppe (ID 1) auf Seite "first" (ID 1)
INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date)
VALUES (1, 'group', 1, 'edit', 1, NOW(), 1, NOW());

-- Berechtigungen für Benutzer "user" (ID 2) auf Seite "first" (ID 1) - nur anzeigen
INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date)
VALUES (1, 'user', 2, 'view', 1, NOW(), 1, NOW());

-- Berechtigungen für Gruppe "users" (ID 2) auf Seite "second" (ID 2) - nur anzeigen
INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date)
VALUES (2, 'group', 2, 'view', 1, NOW(), 1, NOW());

-- Berechtigungen für Gäste auf Seite "first" (ID 1)
INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date)
VALUES (1, 'guest', NULL, 'view', 1, NOW(), 1, NOW());

-- Berechtigungen für Gäste auf Seite "second" (ID 2)
INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date)
VALUES (2, 'guest', NULL, 'view', 1, NOW(), 1, NOW());

-- Berechtigungen für Gäste auf Seite "third" (ID 3) - keine Rechte
INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date)
VALUES (3, 'guest', NULL, 'none', 1, NOW(), 1, NOW());