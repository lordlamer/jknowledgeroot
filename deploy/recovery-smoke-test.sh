#!/bin/bash
set -euo pipefail
umask 077
[[ $# == 3 ]] || { echo 'Usage: recovery-smoke-test.sh NEW_IMAGE PREVIOUS_IMAGE NEW_DATABASE_IMAGE' >&2; exit 2; }
new_image=$1 old_image=$2 new_database=$3
# Preserve the old database alongside the old app; never downgrade an upgraded volume.
old_database=mariadb:12.2.2@sha256:e16f61b8f6ed25111adbb1c5c19bbc2904efc8ed14029999af0cbe1c7ae18bf1
root=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
node=${NODE_BINARY:-node}
test_id="knowledgeroot-recovery-$(date +%s)-$$"
work=$(mktemp -d)
unset KR_APP_IMAGE KR_DB_IMAGE KR_DB_ROOT_PASSWORD KR_DB_PASSWORD KR_MIGRATION_PASSWORD KR_BOOTSTRAP_LOGIN KR_BOOTSTRAP_PASSWORD KR_APP_PORT KR_FORWARD_HEADERS_STRATEGY KR_TRUSTED_PROXY_PATTERN
compose() { docker compose --project-name "$test_id-$stage" --env-file "$work/$stage.env" -f "$root/deploy/compose.production.yaml" "$@"; }
cleanup() {
  for stage in source upgraded rollback; do
    if [[ -f "$work/$stage.env" ]]; then compose down --volumes --remove-orphans >/dev/null 2>&1 || true; fi
  done
  # mktemp allocated this exact directory; leave backup payloads for inspection on failure.
  rm -f -- "$work/source.env" "$work/upgraded.env" "$work/rollback.env"
}
trap cleanup EXIT
trap 'exit 1' INT TERM
export RECOVERY_TEST_PASSWORD="$(openssl rand -hex 24)"
for stage in source upgraded rollback; do
  image=$old_image
  [[ "$stage" != upgraded ]] || image=$new_image
  {
    printf 'KR_APP_IMAGE=%s\n' "$image"
    if [[ "$stage" == upgraded ]]; then
      printf 'KR_DB_IMAGE=%s\n' "$new_database"
    else
      printf 'KR_DB_IMAGE=%s\n' "$old_database"
    fi
    printf 'KR_DB_ROOT_PASSWORD=%s\nKR_DB_PASSWORD=%s\nKR_MIGRATION_PASSWORD=%s\n' "$(openssl rand -hex 32)" "$(openssl rand -hex 32)" "$(openssl rand -hex 32)"
    printf 'KR_BOOTSTRAP_LOGIN=recovery.admin\nKR_BOOTSTRAP_PASSWORD=%s\nKR_APP_PORT=0\n' "$RECOVERY_TEST_PASSWORD"
  } > "$work/$stage.env"
done
sql() { compose exec -T database sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb --protocol=socket --user=root --batch --skip-column-names knowledgeroot'; }
verify() {
  database_version=$(printf 'SELECT VERSION();\n' | sql)
  expected_version=12.2.2
  [[ "$stage" != upgraded ]] || expected_version=12.3.3
  [[ "$database_version" == "$expected_version"-* ]] || { echo "Unexpected $stage database: $database_version" >&2; exit 1; }
  printf '%s database version verified: %s\n' "$stage" "$database_version"
  "$node" "$root/deploy/recovery-http.mjs" "http://$(compose port app 8081)"
  [[ $(printf 'SELECT COUNT(*) FROM tag_content WHERE page_id=102;\n' | sql) == 1 ]]
  [[ $(printf 'SELECT COUNT(*) FROM page_comment WHERE page_id=102;\n' | sql) == 1 ]]
  [[ $(printf 'SELECT COUNT(*) FROM user_page_star WHERE page_id=102;\n' | sql) == 1 ]]
}
stage=source
compose up -d --wait --wait-timeout 120
compose stop app
key="sha256-$(printf 'recovery attachment\n' | sha256sum | cut -d' ' -f1)"
sql <<SQL
INSERT INTO user (id,login,password,active,created_by,create_date,changed_by,change_date) SELECT 101,'recovery.reader',password,TRUE,id,NOW(),id,NOW() FROM user WHERE login='recovery.admin';
INSERT INTO user (id,login,password,active,created_by,create_date,changed_by,change_date) SELECT 102,'recovery.outsider',password,TRUE,id,NOW(),id,NOW() FROM user WHERE login='recovery.admin';
INSERT INTO page (id,name,content,active,created_by,create_date,changed_by,change_date) VALUES (101,'Public','recovery public',TRUE,101,NOW(),101,NOW()),(102,'Private','recovery private',TRUE,101,NOW(),101,NOW());
INSERT INTO \`group\` (id,name,active,created_by,create_date,changed_by,change_date) VALUES (101,'Recovery readers',TRUE,101,NOW(),101,NOW());
INSERT INTO group_member (group_id,member_id,member_type) VALUES (101,101,'user');
INSERT INTO page_permission (page_id,role_type,role_id,permission_level,created_by,create_date,changed_by,change_date) VALUES (101,'guest',NULL,'view',101,NOW(),101,NOW()),(102,'group',101,'view',101,NOW(),101,NOW());
INSERT INTO file (id,page_id,hash,name,size,type,created_by,create_date,changed_by,change_date) VALUES (101,102,'$key','attachment.txt',20,'text/plain',101,NOW(),101,NOW());
INSERT INTO tag (id,name) VALUES (101,'recovery-label');
INSERT INTO tag_content (tag_id,page_id) VALUES (101,102);
INSERT INTO user_page_star (user_id,page_id,create_date) VALUES (101,102,NOW());
INSERT INTO page_comment (page_id,user_id,content,create_date) VALUES (102,101,'recovery comment',NOW());
SQL
compose run --rm --no-deps -T --entrypoint sh app -c 'printf "recovery attachment\n" > "/var/lib/knowledgeroot/files/$1"' sh "$key"
compose up -d --wait --wait-timeout 120
verify
if bash "$root/deploy/recovery.sh" backup "$test_id-$stage" "$work/$stage.env" "$work/unsafe"; then echo 'Backup accepted a running writer' >&2; exit 1; fi
compose stop app
bash "$root/deploy/recovery.sh" backup "$test_id-$stage" "$work/$stage.env" "$work/backup"

stage=upgraded
compose up -d --wait --wait-timeout 120 database
# Corruption must be detected before a single target table is created.
printf 'broken' >> "$work/backup/database.sql"
if bash "$root/deploy/recovery.sh" restore "$test_id-$stage" "$work/$stage.env" "$work/backup"; then exit 1; fi
[[ $(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='knowledgeroot';\n" | sql) == 0 ]]
# Re-create the source backup instead of accepting modified checksums.
stage=source
bash "$root/deploy/recovery.sh" backup "$test_id-$stage" "$work/$stage.env" "$work/pristine"
stage=upgraded
compose run --rm --no-deps -T --entrypoint sh app -c 'touch /var/lib/knowledgeroot/files/restore-sentinel'
if bash "$root/deploy/recovery.sh" restore "$test_id-$stage" "$work/$stage.env" "$work/pristine"; then echo 'Restore accepted a non-empty file volume' >&2; exit 1; fi
[[ $(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='knowledgeroot';\n" | sql) == 0 ]]
compose run --rm --no-deps -T --entrypoint sh app -c 'test -f /var/lib/knowledgeroot/files/restore-sentinel && rm /var/lib/knowledgeroot/files/restore-sentinel'
bash "$root/deploy/recovery.sh" restore "$test_id-$stage" "$work/$stage.env" "$work/pristine"
if bash "$root/deploy/recovery.sh" restore "$test_id-$stage" "$work/$stage.env" "$work/pristine"; then echo 'Restore overwrote an existing database' >&2; exit 1; fi
compose up -d --wait --wait-timeout 120
verify
printf "UPDATE page SET content='post-upgrade change' WHERE id=102;\n" | sql
compose stop app

# Rollback uses the pre-upgrade database AND files with the prior immutable image.
stage=rollback
compose up -d --wait --wait-timeout 120 database
bash "$root/deploy/recovery.sh" restore "$test_id-$stage" "$work/$stage.env" "$work/pristine"
compose up -d --wait --wait-timeout 120
verify
printf 'Recovery, upgrade and snapshot rollback passed. Backup fixtures: %s\n' "$work"
