#!/usr/bin/env bash
set -euo pipefail

required_vars=(
  AWS_REGION
  AWS_S3_BUCKET
  DB_HOST
  DB_NAME
  DB_PASSWORD
  DB_USERNAME
)

for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "mysql_s3_backup.missing_env var=${var_name}" >&2
    exit 1
  fi
done

DB_PORT="${DB_PORT:-3306}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
BACKUP_S3_PREFIX="${BACKUP_S3_PREFIX:-mysql}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
MYSQLDUMP_EXTRA_OPTS="${MYSQLDUMP_EXTRA_OPTS:-}"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
safe_db_name="$(printf '%s' "${DB_NAME}" | tr -c 'A-Za-z0-9_.-' '_')"
backup_file="${BACKUP_DIR}/${safe_db_name}-${timestamp}.sql.gz"
s3_uri="s3://${AWS_S3_BUCKET}/${BACKUP_S3_PREFIX}/${safe_db_name}-${timestamp}.sql.gz"

mkdir -p "${BACKUP_DIR}"

echo "mysql_s3_backup.started db=${DB_NAME} host=${DB_HOST} s3_uri=${s3_uri}"

export MYSQL_PWD="${DB_PASSWORD}"

# shellcheck disable=SC2086
mysqldump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --single-transaction \
  --quick \
  --no-tablespaces \
  --routines \
  --triggers \
  --events \
  ${MYSQLDUMP_EXTRA_OPTS} \
  "${DB_NAME}" | gzip -9 > "${backup_file}"

unset MYSQL_PWD

aws s3 cp "${backup_file}" "${s3_uri}" \
  --region "${AWS_REGION}" \
  --only-show-errors

find "${BACKUP_DIR}" -type f -name '*.sql.gz' -mtime +"${BACKUP_RETENTION_DAYS}" -delete

echo "mysql_s3_backup.completed file=${backup_file} s3_uri=${s3_uri}"
