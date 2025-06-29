-- knowledgeroot dump for mariadb

-- tables

-- table: user
CREATE TABLE `user` (id integer NOT NULL AUTO_INCREMENT,
first_name varchar(255) DEFAULT '' NOT NULL,
last_name varchar(255) DEFAULT '' NOT NULL,
login varchar(255) DEFAULT '' NOT NULL,
email varchar(255) DEFAULT '' NOT NULL,
password varchar(255) DEFAULT '' NOT NULL,
admin tinyint(1) DEFAULT false NOT NULL,
language varchar(10) DEFAULT '' NOT NULL,
timezone varchar(50) DEFAULT 'UTC' NOT NULL,
time_start datetime NULL,
time_end datetime NULL,
active tinyint(1) DEFAULT false NOT NULL,
created_by integer NOT NULL,
create_date datetime NOT NULL,
changed_by integer NOT NULL,
change_date datetime NOT NULL,
deleted tinyint(1) DEFAULT false NOT NULL,
PRIMARY KEY (id)
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

-- table: group
CREATE TABLE `group` (
                         id integer NOT NULL AUTO_INCREMENT,
                         name varchar(255) DEFAULT '' NOT NULL,
                         description text DEFAULT '' NOT NULL,
                         time_start datetime NULL,
                         time_end datetime NULL,
                         active tinyint(1) DEFAULT false NOT NULL,
                         created_by integer NOT NULL,
                         create_date datetime NOT NULL,
                         changed_by integer NOT NULL,
                         change_date datetime NOT NULL,
                         deleted tinyint(1) DEFAULT false NOT NULL,
                         PRIMARY KEY (id)
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

-- table: user_group
CREATE TABLE group_member (
                              id integer NOT NULL AUTO_INCREMENT,
                              group_id integer NOT NULL,
                              member_id integer NOT NULL,
                              member_type ENUM('user', 'group') NOT NULL,
                              PRIMARY KEY (id)
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

-- table: acl
CREATE TABLE acl (
                     id integer NOT NULL AUTO_INCREMENT,
                     role_id varchar(255) NOT NULL,
                     resource varchar(255) NOT NULL,
                     action ENUM('new','edit','delete','show','new_content','permission','print','export') NOT NULL,
                     `right` ENUM('allow', 'deny') NOT NULL,
                     PRIMARY KEY (id)
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

-- table: page
CREATE TABLE page (
                      id integer NOT NULL AUTO_INCREMENT,
                      parent integer DEFAULT 0 NOT NULL,
                      name varchar(255) DEFAULT '' NOT NULL,
                      content text DEFAULT '' NOT NULL,
                      time_start datetime NULL,
                      time_end datetime NULL,
                      created_by integer NOT NULL,
                      create_date datetime NOT NULL,
                      changed_by integer NOT NULL,
                      change_date datetime NOT NULL,
                      active tinyint(1) DEFAULT false NOT NULL,
                      deleted tinyint(1) DEFAULT false NOT NULL,
                      PRIMARY KEY (id),
                      FOREIGN KEY (created_by) REFERENCES `user` (id) ON DELETE RESTRICT,
                      FOREIGN KEY (changed_by) REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

-- table: page_permission
CREATE TABLE page_permission (
                                 id integer NOT NULL AUTO_INCREMENT,
                                 page_id integer NOT NULL,
                                 role_type ENUM('user', 'group', 'guest') NOT NULL,
                                 role_id integer NULL,  -- NULL for guest users
                                 permission_level ENUM('none', 'view', 'edit') DEFAULT 'none' NOT NULL,
                                 created_by integer NOT NULL,
                                 create_date datetime NOT NULL,
                                 changed_by integer NOT NULL,
                                 change_date datetime NOT NULL,
                                 PRIMARY KEY (id),
                                 FOREIGN KEY (page_id) REFERENCES page (id) ON DELETE CASCADE,
                                 FOREIGN KEY (created_by) REFERENCES `user` (id) ON DELETE RESTRICT,
                                 FOREIGN KEY (changed_by) REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

-- indexes for table: page_permission
CREATE INDEX idx_page_permission_page ON page_permission (page_id);
CREATE INDEX idx_page_permission_role ON page_permission (role_type, role_id);

-- table: page
CREATE TABLE page_history (
                              id integer NOT NULL AUTO_INCREMENT,
                              page_id integer DEFAULT 0 NOT NULL,
                              version integer DEFAULT 0 NOT NULL,
                              parent integer DEFAULT 0 NOT NULL,
                              name varchar(255) DEFAULT '' NOT NULL,
                              content text DEFAULT '' NOT NULL,
                              time_start datetime NULL,
                              time_end datetime NULL,
                              created_by integer NOT NULL,
                              create_date datetime NOT NULL,
                              changed_by integer NOT NULL,
                              change_date datetime NOT NULL,
                              active tinyint(1) DEFAULT false NOT NULL,
                              deleted tinyint(1) DEFAULT false NOT NULL,
                              PRIMARY KEY (id),
                              FOREIGN KEY (page_id) REFERENCES page (id) ON DELETE CASCADE
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

-- table: file
CREATE TABLE file (
                      id integer NOT NULL AUTO_INCREMENT,
                      page_id integer DEFAULT 0 NOT NULL,
                      hash varchar(32) DEFAULT '' NOT NULL,
                      name varchar(255) DEFAULT '' NOT NULL,
                      size integer DEFAULT 0 NOT NULL,
                      type varchar(255) DEFAULT 'application/octet-stream',
                      downloads integer DEFAULT 0 NOT NULL,
                      created_by integer NOT NULL,
                      create_date datetime NOT NULL,
                      changed_by integer NOT NULL,
                      change_date datetime NOT NULL,
                      deleted tinyint(1) DEFAULT false NOT NULL,
                      PRIMARY KEY (id),
                      FOREIGN KEY (page_id) REFERENCES page (id) ON DELETE CASCADE,
                      FOREIGN KEY (created_by) REFERENCES `user` (id) ON DELETE RESTRICT,
                      FOREIGN KEY (changed_by) REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

-- tags

-- table: tag
CREATE TABLE tag (
                     id integer NOT NULL AUTO_INCREMENT,
                     name varchar(255) DEFAULT '' NOT NULL,
                     PRIMARY KEY (id)
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

-- table: tag_content
CREATE TABLE tag_content (
                             id integer NOT NULL AUTO_INCREMENT,
                             tag_id integer NOT NULL,
                             page_id integer NOT NULL,
                             PRIMARY KEY (id),
                             FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE,
                             FOREIGN KEY (page_id) REFERENCES page (id) ON DELETE CASCADE
) ENGINE=INNODB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1;

-- content ratings

-- indexes

-- data

-- guest user

