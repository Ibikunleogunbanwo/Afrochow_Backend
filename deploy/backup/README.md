# Database Backups to S3

This folder contains the Docker image and scripts used by the `db-backup` service in `docker-compose.prod.yml`.

The service runs a MySQL logical backup with `mysqldump`, compresses the SQL file with gzip, uploads it to an Amazon S3 bucket, and then keeps the cron worker alive for scheduled runs.

Current production backup target:

```text
s3://afrochow-db-backups-prod-740204037801-ca-central-1-an/mysql/
```

## Architecture

```text
VPS Docker network

afrochow-mysql        MySQL database
afrochow-db-backup    mysqldump + gzip + aws s3 cp + cron
Amazon S3             encrypted private backup bucket
```

The backup container connects to MySQL using the internal Docker hostname:

```env
DB_HOST=mysql
DB_PORT=3306
```

It uploads backups to:

```text
s3://$AWS_S3_BACKUP_BUCKET/$DB_BACKUP_S3_PREFIX/
```

## Files

```text
deploy/backup/Dockerfile
deploy/backup/backup-entrypoint.sh
deploy/backup/backup-mysql-to-s3.sh
deploy/backup/README.md
```

The Dockerfile uses `mysql:8.4` as the base image so the bundled `mysqldump` client matches the MySQL 8.4 server authentication plugins.

## AWS Bucket Setup

Create an S3 bucket in `ca-central-1`.

Recommended bucket settings:

```text
Bucket namespace: Account Regional namespace, if available
Object ownership: ACLs disabled
Block public access: Block all public access
Bucket versioning: Enabled
Default encryption: SSE-S3
Bucket key: Enabled
```

Production bucket:

```text
afrochow-db-backups-prod-740204037801-ca-central-1-an
```

## S3 Lifecycle Rule

Create a lifecycle rule on the bucket:

```text
Rule name: expire-old-db-backups
Prefix: mysql/
Expire current versions: 90 days
Permanently delete noncurrent versions: 30 days
Delete expired object delete markers: enabled
```

The container only deletes old local files in its Docker volume. Long-term remote retention should be handled by S3 lifecycle rules.

## IAM Policy

Create an IAM policy named:

```text
AfrochowDbBackupS3Policy
```

Policy JSON:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListBackupBucket",
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket"
      ],
      "Resource": "arn:aws:s3:::afrochow-db-backups-prod-740204037801-ca-central-1-an",
      "Condition": {
        "StringLike": {
          "s3:prefix": [
            "mysql/*"
          ]
        }
      }
    },
    {
      "Sid": "WriteAndReadDatabaseBackups",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::afrochow-db-backups-prod-740204037801-ca-central-1-an/mysql/*"
    }
  ]
}
```

Create an IAM user named:

```text
afrochow-db-backup
```

Attach `AfrochowDbBackupS3Policy`, then create an access key for an application running outside AWS.

Do not commit the access key. Store it only in `.env.prod` on the VPS.

## VPS Environment

Add these values to `/opt/afrochow/.env.prod`:

```env
AWS_REGION=ca-central-1
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
AWS_S3_BACKUP_BUCKET=afrochow-db-backups-prod-740204037801-ca-central-1-an
DB_BACKUP_CRON=0 2 * * *
DB_BACKUP_TIMEZONE=America/Edmonton
DB_BACKUP_S3_PREFIX=mysql
DB_BACKUP_LOCAL_RETENTION_DAYS=7
DB_BACKUP_ONESHOT=false
DB_BACKUP_RUN_ON_STARTUP=false
```

Protect the env file:

```bash
chmod 600 /opt/afrochow/.env.prod
```

Check required env vars without printing secrets:

```bash
cd /opt/afrochow
set -a
. ./.env.prod
set +a

for v in AWS_REGION AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_S3_BACKUP_BUCKET DB_NAME DB_USERNAME DB_PASSWORD; do
  if [ -n "${!v}" ]; then echo "$v=OK"; else echo "$v=MISSING"; fi
done
```

## Deploy Backup Files

From the laptop:

```bash
cd /Users/ayoogunbanwo/software-project/Afrochow-Backend
rsync -avz docker-compose.prod.yml root@65.21.111.29:/opt/afrochow/docker-compose.prod.yml
ssh root@65.21.111.29 "mkdir -p /opt/afrochow/deploy/backup"
rsync -avz deploy/backup/ root@65.21.111.29:/opt/afrochow/deploy/backup/
```

On the VPS:

```bash
cd /opt/afrochow
chown -R root:root docker-compose.prod.yml deploy/backup
docker compose -f docker-compose.prod.yml config --quiet
docker compose -f docker-compose.prod.yml build db-backup
```

## Test a Backup Immediately

Run a one-shot backup:

```bash
cd /opt/afrochow
docker compose -f docker-compose.prod.yml run --rm -e BACKUP_ONESHOT=true db-backup
```

Expected success:

```text
mysql_s3_backup.started db=afrochowdb host=mysql s3_uri=s3://afrochow-db-backups-prod-740204037801-ca-central-1-an/mysql/afrochowdb-YYYYMMDDTHHMMSSZ.sql.gz
mysql_s3_backup.completed file=/backups/afrochowdb-YYYYMMDDTHHMMSSZ.sql.gz s3_uri=s3://afrochow-db-backups-prod-740204037801-ca-central-1-an/mysql/afrochowdb-YYYYMMDDTHHMMSSZ.sql.gz
```

Confirm the uploaded file in S3 under:

```text
mysql/
```

## Start Scheduled Backups

Start the cron worker:

```bash
cd /opt/afrochow
docker compose -f docker-compose.prod.yml up -d db-backup
```

Verify it is running:

```bash
docker ps | grep afrochow-db-backup
docker logs --tail=50 afrochow-db-backup
```

Expected scheduler log:

```text
mysql_s3_backup.scheduler_started cron='0 2 * * *' timezone='America/Edmonton'
```

This runs daily at 2:00 AM America/Edmonton.

## Troubleshooting

If `mysqldump` fails with `caching_sha2_password could not be loaded`, the image is using the wrong MySQL client. The backup image must be based on `mysql:8.4`.

If `mysqldump` fails with a `PROCESS privilege` warning for tablespaces, make sure `backup-mysql-to-s3.sh` includes:

```bash
--no-tablespaces
```

If `docker compose` says `no such service: db-backup`, deploy the updated `docker-compose.prod.yml` and `deploy/backup/` folder to `/opt/afrochow`.

If the scheduler log is the only log, that is normal until the next cron run. Use `BACKUP_ONESHOT=true` for an immediate test.

## Restore Notes

To inspect a backup locally:

```bash
gunzip -c afrochowdb-YYYYMMDDTHHMMSSZ.sql.gz | head
```

To restore into MySQL, copy the backup to the target machine, decompress it, and import:

```bash
gunzip -c afrochowdb-YYYYMMDDTHHMMSSZ.sql.gz | mysql -h HOST -u USER -p DATABASE
```

Always test restores against a staging or disposable database before relying on backups operationally.
