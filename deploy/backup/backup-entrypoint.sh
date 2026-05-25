#!/usr/bin/env sh
set -eu

: "${BACKUP_CRON:=0 2 * * *}"
: "${BACKUP_TIMEZONE:=UTC}"
: "${BACKUP_ONESHOT:=false}"
: "${RUN_BACKUP_ON_STARTUP:=false}"

mkdir -p /backups /etc/backup /var/log/backup
ln -snf "/usr/share/zoneinfo/${BACKUP_TIMEZONE}" /etc/localtime
echo "${BACKUP_TIMEZONE}" > /etc/timezone

printenv | awk '
  BEGIN { q = sprintf("%c", 39) }
  /^[A-Za-z_][A-Za-z0-9_]*=/ {
    key = $0
    sub(/=.*/, "", key)
    value = substr($0, length(key) + 2)
    gsub(q, q "\\" q q, value)
    print "export " key "=" q value q
  }
' > /etc/backup/env
chmod 600 /etc/backup/env

cat > /tmp/backup-crontab <<EOF
${BACKUP_CRON} . /etc/backup/env; /usr/local/bin/backup-mysql-to-s3.sh >> /var/log/backup/mysql-s3.log 2>&1
EOF
crontab /tmp/backup-crontab
rm -f /tmp/backup-crontab

echo "mysql_s3_backup.scheduler_started cron='${BACKUP_CRON}' timezone='${BACKUP_TIMEZONE}'"

if [ "${BACKUP_ONESHOT}" = "true" ]; then
  echo "mysql_s3_backup.oneshot_started"
  exec /usr/local/bin/backup-mysql-to-s3.sh
fi

if [ "${RUN_BACKUP_ON_STARTUP}" = "true" ]; then
  echo "mysql_s3_backup.startup_run_started"
  /usr/local/bin/backup-mysql-to-s3.sh
fi

exec crond -n
