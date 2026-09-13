#!/bin/sh
# Disposable deployment verification; never uses the operator's .env or existing volumes.
set -eu
umask 077
unset KR_APP_IMAGE KR_DB_ROOT_PASSWORD KR_DB_PASSWORD KR_MIGRATION_PASSWORD KR_BOOTSTRAP_LOGIN KR_BOOTSTRAP_PASSWORD KR_APP_PORT KR_FORWARD_HEADERS_STRATEGY KR_TRUSTED_PROXY_PATTERN
root=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
project="knowledgeroot-smoke-$(date +%s)-$$"
envfile=$(mktemp)
compose() { docker compose --project-name "$project" --env-file "$envfile" -f "$root/deploy/compose.production.yaml" "$@"; }
cleanup() {
  compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -f "$envfile"
}
trap cleanup EXIT
trap 'exit 1' INT TERM
{
  printf 'KR_APP_IMAGE=%s\n' "${1:?Pass the locally built image tag}"
  printf 'KR_DB_ROOT_PASSWORD=%s\n' "$(openssl rand -hex 32)"
  printf 'KR_DB_PASSWORD=%s\n' "$(openssl rand -hex 32)"
  printf 'KR_MIGRATION_PASSWORD=%s\n' "$(openssl rand -hex 32)"
  printf 'KR_BOOTSTRAP_LOGIN=deployment.admin\n'
  printf 'KR_BOOTSTRAP_PASSWORD=%s\n' "$(openssl rand -hex 32)"
  printf 'KR_APP_PORT=0\n'
} > "$envfile"
compose config --quiet
compose up --detach --wait --wait-timeout 120
address=$(compose port app 8081)
ready=false
for attempt in $(seq 1 90); do
  if curl --connect-timeout 1 --max-time 2 --fail --silent "http://$address/login" >/dev/null; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then compose logs app; exit 1; fi
test "$(curl --max-time 10 --fail --silent "http://$address/actuator/health/readiness")" = '{"status":"UP"}'
compose exec -T app sh -c 'bash /app/healthcheck'
compose exec -T app sh -c '
  set -eu
  test "$(id -u)" = 10001
  for package in fontconfig libfontconfig1 libexpat1; do
    if dpkg-query --status "$package" >/dev/null 2>&1; then
      echo "Unexpected server font/XML package: $package" >&2
      exit 1
    fi
  done
  touch /var/lib/knowledgeroot/files/.deployment-write-test
  rm /var/lib/knowledgeroot/files/.deployment-write-test
  if touch /app/forbidden-write 2>/dev/null; then exit 1; fi
'
compose exec -T database sh -c '
  set -eu
  export MYSQL_PWD="$KR_DB_PASSWORD"
  count=$(mariadb --user=knowledgeroot knowledgeroot --execute="SELECT COUNT(*) FROM user" --skip-column-names)
  test "$count" = 1
  if mariadb --user=knowledgeroot knowledgeroot --execute="CREATE TABLE forbidden_ddl (id INT)" 2>/dev/null; then exit 1; fi
'
compose restart app
address=$(compose port app 8081)
ready=false
for attempt in $(seq 1 90); do
  if curl --connect-timeout 1 --max-time 2 --fail --silent "http://$address/login" >/dev/null; then ready=true; break; fi
  sleep 1
done
test "$ready" = true
compose exec -T app sh -c 'bash /app/healthcheck'
if compose logs app | grep -qi 'password='; then
  printf 'Application startup logs must not contain JDBC passwords.\n' >&2
  exit 1
fi
printf 'Production deployment smoke test passed (including restart).\n'
