-- Dump for Postgresql with demo data

-- user
INSERT INTO `user` (first_name, last_name, login, email, password, language, timezone, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ('knowledgeroot', 'admin', 'admin', 'admin@localhost', '$5$100$c4d60033$3997ea4e9be21d976ae54355c29a27f56a776607e8c3e7ad99a120d14a5ae9d69730e08595d0a0576bfd248d30c52aa6f9ed8e1581d307d469be3fe960325814', 'en_US', 'Europe/Berlin', true, 0, '2012-10-01 22:00:00', 0, '2012-10-01 22:00:00', false);

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
INSERT INTO page (parent, name, active, created_by, create_date, changed_by, change_date, deleted)
VALUES (0, 'first', true, (SELECT id FROM `user` where login='admin'), '2012-10-01 22:00:00', (SELECT id FROM `user` where login='admin'), '2012-10-01 22:00:00', false);

INSERT INTO page (parent, name, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ((SELECT x.id FROM (SELECT * FROM page) x where x.name='first'), 'second', true, (SELECT x.id FROM (SELECT * FROM `user`) x where x.login='admin'), '2012-10-01 22:00:00', (SELECT x.id FROM (SELECT * FROM `user`) x where x.login='admin'), '2012-10-01 22:00:00', false);

INSERT INTO page (parent, name, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ((SELECT x.id FROM (SELECT * FROM page) x where x.name='second'), 'third', true, (SELECT x.id FROM (SELECT * FROM `user`) x where x.login='admin'), '2012-10-01 22:00:00', (SELECT x.id FROM (SELECT * FROM `user`) x where x.login='admin'), '2012-10-01 22:00:00', false);

-- content
INSERT INTO content (parent, name, content, active, created_by, create_date, changed_by, change_date, deleted)
VALUES ((SELECT id FROM page where name='first'), 'first content', 'first content', true, (SELECT id FROM `user` where login='admin'), '2012-10-01 22:00:00', (SELECT id FROM `user` where login='admin'), '2012-10-01 22:00:00', false);

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
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'content_1', 'edit', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'content_1', 'delete', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'content_1', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'content_1', 'print', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_1', 'content_1', 'permission', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_0', 'page_1', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_0', 'page_1', 'new_content', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_0', 'content_1', 'show', 'allow');
INSERT INTO acl (role_id, resource, action, `right`) VALUES ('U_0', 'content_1', 'print', 'allow');