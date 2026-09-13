#!/bin/bash
# Offline logical backup/restore for the single-instance production Compose deployment.
set -euo pipefail
umask 077
[[ $# == 4 ]] || { echo 'Usage: recovery.sh backup|restore PROJECT ENV_FILE NEW_BACKUP_DIRECTORY' >&2; exit 2; }
operation=$1 project=$2 envfile=$3 directory=$4
[[ "$operation" == backup || "$operation" == restore ]] || exit 2
[[ "$project" =~ ^[a-z0-9][a-z0-9_-]+$ ]] || { echo 'Invalid explicit project name' >&2; exit 2; }
[[ -f "$envfile" ]] || { echo 'Environment file missing' >&2; exit 2; }
root=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
# Explicit files take precedence over unrelated operator shell variables.
unset KR_APP_IMAGE KR_DB_IMAGE KR_DB_ROOT_PASSWORD KR_DB_PASSWORD KR_MIGRATION_PASSWORD KR_BOOTSTRAP_LOGIN KR_BOOTSTRAP_PASSWORD KR_APP_PORT KR_FORWARD_HEADERS_STRATEGY KR_TRUSTED_PROXY_PATTERN
compose() { docker compose --project-name "$project" --env-file "$envfile" -f "$root/deploy/compose.production.yaml" "$@"; }
fail() { printf '%s\n' "$1" >&2; exit 1; }
compose config --quiet
for container in $(compose ps --all --quiet app); do
  [[ $(docker inspect --format '{{.State.Running}}' "$container") == false ]] || fail 'Stop the application and all other writers first.'
done
[[ -n "$(compose ps --status running --quiet database)" ]] || fail 'The database must already be running.'
sql() { compose exec -T database sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb --protocol=socket --user=root --batch --skip-column-names knowledgeroot'; }
files() { compose run --rm --no-deps -T --entrypoint sh app -c "$1"; }

if [[ "$operation" == backup ]]; then
  app=$(compose ps --all --quiet app)
  [[ -n "$app" ]] || fail 'A stopped application container is required to identify the backed-up version.'
  mkdir -- "$directory" # Refuse reuse; failed backups remain incomplete for inspection.
  printf 'incomplete\n' > "$directory/INCOMPLETE"
  compose exec -T database sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb-dump --protocol=socket --user=root --single-transaction --quick --hex-blob --routines --events --triggers knowledgeroot' > "$directory/database.sql"
  files 'tar -czf - -C /var/lib/knowledgeroot/files .' > "$directory/files.tar.gz"
  {
    printf 'format=1\ncreated_utc=%s\nstorage=file\n' "$(date -u +%FT%TZ)"
    printf 'app_image_id=%s\n' "$(docker inspect --format '{{.Image}}' "$app")"
    printf 'database_image_id=%s\n' "$(docker inspect --format '{{.Image}}' "$(compose ps --quiet database)")"
    printf 'database_image_ref=%s\n' "$(docker inspect --format '{{.Config.Image}}' "$(compose ps --quiet database)")"
    printf 'database_version=%s\n' "$(printf 'SELECT VERSION();\n' | sql)"
  } > "$directory/manifest.txt"
  (cd -- "$directory" && sha256sum database.sql files.tar.gz manifest.txt > SHA256SUMS)
  rm -- "$directory/INCOMPLETE"
  printf 'Backup complete. Application remains stopped: %s\n' "$directory"
else
  [[ -d "$directory" && ! -e "$directory/INCOMPLETE" ]] || fail 'Backup missing or incomplete.'
  # Fixed filenames only; do not let a supplied checksum list name arbitrary paths.
  [[ $(wc -l < "$directory/SHA256SUMS") == 3 ]] || fail 'Invalid checksum list.'
  for name in database.sql files.tar.gz manifest.txt; do
    [[ -f "$directory/$name" && ! -L "$directory/$name" ]] || fail 'Backup payload missing or a symbolic link.'
    expected=$(awk -v name="$name" '$2 == name || $2 == "*" name {print $1}' "$directory/SHA256SUMS")
    [[ "$expected" =~ ^[a-f0-9]{64}$ ]] || fail 'Invalid checksum entry.'
    actual=$(sha256sum "$directory/$name"); actual=${actual%% *}
    [[ "$actual" == "$expected" ]] || fail "Checksum mismatch: $name"
  done
  grep -qx 'format=1' "$directory/manifest.txt" || fail 'Unsupported backup format.'
  grep -qx 'storage=file' "$directory/manifest.txt" || fail 'Unsupported storage driver.'
  tar -tzf "$directory/files.tar.gz" > /dev/null
  [[ $(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='knowledgeroot';\n" | sql) == 0 ]] || fail 'Restore requires an empty database; existing data is never overwritten.'
  files 'test -z "$(find /var/lib/knowledgeroot/files -mindepth 1 -print -quit)"' || fail 'Restore requires an empty file volume.'
  # Restore only trusted own backups: SQL is executable, checksums are not signatures.
  sql < "$directory/database.sql"
  files 'tar --no-same-owner -xzf - -C /var/lib/knowledgeroot/files' < "$directory/files.tar.gz"
  printf 'Restore complete. Validate the data, then explicitly start the application.\n'
fi
