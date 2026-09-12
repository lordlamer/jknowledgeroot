#!/bin/sh
# Runs only when initializing a new MariaDB volume. Passwords are never logged.
(
  set -eu
  for password in "$KR_DB_PASSWORD" "$KR_MIGRATION_PASSWORD"; do
    case "$password" in *[!A-Za-z0-9_-]*|'') echo 'Database passwords must use URL-safe letters, digits, _ or -' >&2; exit 1;; esac
    if [ "${#password}" -lt 32 ]; then echo 'Database passwords need at least 32 characters' >&2; exit 1; fi
  done
  MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb --protocol=socket --user=root <<SQL
CREATE USER 'knowledgeroot'@'%' IDENTIFIED BY '$KR_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON knowledgeroot.* TO 'knowledgeroot'@'%';
CREATE USER 'knowledgeroot_migrate'@'%' IDENTIFIED BY '$KR_MIGRATION_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON knowledgeroot.* TO 'knowledgeroot_migrate'@'%';
SQL
)
