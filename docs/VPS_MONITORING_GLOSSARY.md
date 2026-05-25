# VPS Monitoring Glossary

This is the copy-paste command glossary for monitoring Afrochow production on the new VPS.

```text
Production VPS: 65.21.111.29
Project path:   /opt/afrochow
Public API:     https://api.afrochow.ca
Frontend:       https://www.afrochow.ca
Kafka UI:       http://localhost:8088 through SSH tunnel
```

## Connect To The VPS

SSH into production:

```bash
ssh root@65.21.111.29
```

Move to the app folder:

```bash
cd /opt/afrochow
```

Always include the production env file when running Docker Compose:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```

## API Health

Check the public Cloudflare path:

```bash
curl -fsS https://api.afrochow.ca/api/actuator/health
```

Expected:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

Check staging API path:

```bash
curl -fsS https://staging-api.afrochow.ca/api/actuator/health
```

Check the origin Nginx path from inside the VPS:

```bash
curl -kfsS https://127.0.0.1/api/actuator/health -H "Host: api.afrochow.ca"
```

Check the app container directly from Nginx:

```bash
docker exec afrochow-nginx wget -qO- http://afrochow-app:8081/api/actuator/health
```

## DNS And Cloudflare

Check DNS resolution:

```bash
dig +short api.afrochow.ca
```

Check Cloudflare DNS directly:

```bash
dig +short @1.1.1.1 api.afrochow.ca
```

When Cloudflare proxy is enabled, DNS usually returns Cloudflare IPs, not the VPS IP.

Force curl through a known Cloudflare edge IP:

```bash
curl --resolve api.afrochow.ca:443:104.21.46.195 -fsS https://api.afrochow.ca/api/actuator/health
```

Inspect the public TLS certificate:

```bash
echo | openssl s_client -connect api.afrochow.ca:443 -servername api.afrochow.ca -showcerts 2>/dev/null | openssl x509 -noout -issuer -subject -dates
```

## CORS

Check that `www.afrochow.ca` is allowed:

```bash
curl -i -X OPTIONS https://api.afrochow.ca/api/categories \
  -H "Origin: https://www.afrochow.ca" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization,content-type"
```

Healthy response includes:

```text
HTTP/2 200
access-control-allow-origin: https://www.afrochow.ca
access-control-allow-credentials: true
```

Check production CORS env on the VPS:

```bash
cd /opt/afrochow
grep -nE "^(APP_FRONTEND_URL|CORS_ALLOWED_ORIGINS)=" .env.prod
```

After editing CORS values:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps --force-recreate app
```

## Docker Stack

List production containers:

```bash
cd /opt/afrochow
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```

List container status, ports, and restarts:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Show resource use:

```bash
docker stats --no-stream
```

Restart only the Spring Boot app:

```bash
cd /opt/afrochow
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps --build --force-recreate app
```

Restart Nginx only:

```bash
cd /opt/afrochow
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d nginx
```

Restart Kafka UI only:

```bash
cd /opt/afrochow
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d kafka-ui
```

Stop Kafka UI:

```bash
cd /opt/afrochow
docker compose --env-file .env.prod -f docker-compose.prod.yml stop kafka-ui
```

## App Logs

Tail application logs:

```bash
docker logs -f --tail=200 afrochow-app
```

Show recent app logs:

```bash
docker logs --tail=300 afrochow-app
```

Search app logs for errors:

```bash
docker logs --tail=1000 afrochow-app 2>&1 | grep -Ei "error|exception|failed|timeout"
```

Search app logs for Kafka activity:

```bash
docker logs --tail=1000 afrochow-app 2>&1 | grep -Ei "kafka|consumer|domain-events|dlq|acked"
```

Search app logs for notifications:

```bash
docker logs --tail=1000 afrochow-app 2>&1 | grep -i notification
```

Search app logs for safety net runs:

```bash
docker logs --tail=1000 afrochow-app 2>&1 | grep -i SAFETY_NET
```

## Nginx And TLS

Validate Nginx config:

```bash
docker exec afrochow-nginx nginx -t
```

Tail Nginx logs:

```bash
docker logs -f --tail=200 afrochow-nginx
```

Check exposed listeners on the VPS:

```bash
ss -tulpn | grep -E ":80|:443|:8081|:3306|:6379|:9092|:8088"
```

Verify Cloudflare origin certificate files:

```bash
ls -l /opt/afrochow/deploy/nginx/certs
openssl x509 -in /opt/afrochow/deploy/nginx/certs/cloudflare-origin.pem -noout -subject -dates
openssl pkey -in /opt/afrochow/deploy/nginx/certs/cloudflare-origin.key -noout -check
```

Check Nginx origin health:

```bash
curl -kfsS https://127.0.0.1/api/actuator/health -H "Host: api.afrochow.ca"
```

## MySQL

Check MySQL container health:

```bash
docker inspect --format='{{json .State.Health}}' afrochow-mysql
```

Open a MySQL shell:

```bash
cd /opt/afrochow
set -a
. ./.env.prod
set +a
docker exec -it afrochow-mysql mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME"
```

Count important tables:

```bash
cd /opt/afrochow
set -a
. ./.env.prod
set +a
docker exec afrochow-mysql mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -e "SELECT 'users' table_name, COUNT(*) rows_count FROM users UNION ALL SELECT 'orders', COUNT(*) FROM orders UNION ALL SELECT 'payment', COUNT(*) FROM payment UNION ALL SELECT 'notification', COUNT(*) FROM notification UNION ALL SELECT 'outbox_event', COUNT(*) FROM outbox_event;"
```

Check MySQL process list:

```bash
cd /opt/afrochow
set -a
. ./.env.prod
set +a
docker exec afrochow-mysql mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -e "SHOW PROCESSLIST;"
```

## Database Backups

Check the backup container:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}" | grep afrochow-db-backup
```

Show backup logs:

```bash
docker logs --tail=100 afrochow-db-backup
```

Run a one-shot backup:

```bash
cd /opt/afrochow
docker compose --env-file .env.prod -f docker-compose.prod.yml run --rm -e BACKUP_ONESHOT=true db-backup
```

Confirm required backup env vars without printing secrets:

```bash
cd /opt/afrochow
set -a
. ./.env.prod
set +a

for v in AWS_REGION AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_S3_BACKUP_BUCKET DB_NAME DB_USERNAME DB_PASSWORD; do
  if [ -n "${!v}" ]; then echo "$v=OK"; else echo "$v=MISSING"; fi
done
```

List local backup volume files from inside a one-off container:

```bash
cd /opt/afrochow
docker compose --env-file .env.prod -f docker-compose.prod.yml run --rm --entrypoint "bash" db-backup -lc "ls -lh /backups | tail"
```

## Kafka CLI

List topics:

```bash
docker exec -it afrochow-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

Describe the main topic:

```bash
docker exec -it afrochow-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic afrochow.domain-events
```

Describe retry topic:

```bash
docker exec -it afrochow-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic afrochow.domain-events.retry
```

Describe DLQ topic:

```bash
docker exec -it afrochow-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic afrochow.domain-events.dlq
```

Check notification consumer group:

```bash
docker exec -it afrochow-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group afrochow-notification-service
```

Check address geocoding consumer group:

```bash
docker exec -it afrochow-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group afrochow-address-geocoding-service
```

Check payment transfer consumer group:

```bash
docker exec -it afrochow-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group afrochow-payment-transfer-service
```

Interpret consumer group output:

```text
LOG-END-OFFSET > 0  messages have been produced
CURRENT-OFFSET      messages consumed by that group
LAG = 0             consumer is caught up
LAG > 0             messages are waiting or consumer is stuck
```

Read main topic from beginning:

```bash
docker exec -it afrochow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic afrochow.domain-events \
  --from-beginning \
  --timeout-ms 5000
```

Watch new events live:

```bash
docker exec -it afrochow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic afrochow.domain-events
```

Read DLQ events:

```bash
docker exec -it afrochow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic afrochow.domain-events.dlq \
  --from-beginning \
  --timeout-ms 5000
```

## Kafka UI

Kafka UI runs privately on the VPS:

```text
VPS bind: 127.0.0.1:8088
Browser:  http://localhost:8088 through SSH tunnel
```

Start the SSH tunnel from your Mac:

```bash
ssh -L 8088:127.0.0.1:8088 root@65.21.111.29
```

Keep that terminal open, then visit:

```text
http://localhost:8088
```

Open:

```text
afrochow-prod -> Topics -> afrochow.domain-events
afrochow-prod -> Topics -> afrochow.domain-events.dlq
afrochow-prod -> Consumers -> afrochow-notification-service
```

Start the SSH tunnel in the background:

```bash
ssh -fN -L 8088:127.0.0.1:8088 root@65.21.111.29
```

Find the background tunnel:

```bash
ps aux | grep "8088:127.0.0.1:8088"
```

Stop the background tunnel:

```bash
kill <PID>
```

Do not expose Kafka UI publicly unless Cloudflare Access or another authentication layer is configured.

## Redis

Check Redis health:

```bash
docker inspect --format='{{json .State.Health}}' afrochow-redis
```

Ping Redis:

```bash
cd /opt/afrochow
set -a
. ./.env.prod
set +a
docker exec -it afrochow-redis redis-cli -a "$REDIS_PASSWORD" ping
```

Expected:

```text
PONG
```

Check Redis memory:

```bash
cd /opt/afrochow
set -a
. ./.env.prod
set +a
docker exec -it afrochow-redis redis-cli -a "$REDIS_PASSWORD" info memory
```

Test Redis GEO commands:

```bash
cd /opt/afrochow
set -a
. ./.env.prod
set +a
docker exec -it afrochow-redis redis-cli -a "$REDIS_PASSWORD" GEOADD test:vendors -114.0719 51.0447 vendor:calgary
docker exec -it afrochow-redis redis-cli -a "$REDIS_PASSWORD" GEOSEARCH test:vendors FROMLONLAT -114.0719 51.0447 BYRADIUS 10 km
```

## VPS System Health

Disk usage:

```bash
df -h
```

Memory:

```bash
free -h
```

CPU/load:

```bash
uptime
```

Top processes:

```bash
top
```

Recent system logs:

```bash
journalctl -n 100 --no-pager
```

Docker disk usage:

```bash
docker system df
```

List listening ports:

```bash
ss -tulpn
```

## Deployment Safety Checks

Validate Compose:

```bash
cd /opt/afrochow
docker compose --env-file .env.prod -f docker-compose.prod.yml config --quiet
```

Check required env variables without printing secrets:

```bash
cd /opt/afrochow
set -a
. ./.env.prod
set +a

for v in DB_NAME DB_USERNAME DB_PASSWORD REDIS_PASSWORD APP_JWT_SECRET APP_FRONTEND_URL CORS_ALLOWED_ORIGINS AWS_REGION AWS_S3_BACKUP_BUCKET; do
  if [ -n "${!v}" ]; then echo "$v=OK"; else echo "$v=MISSING"; fi
done
```

Rebuild app after code changes:

```bash
cd /opt/afrochow
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps --build --force-recreate app
curl -fsS https://api.afrochow.ca/api/actuator/health
```

Check app startup after rebuild:

```bash
docker logs --tail=150 afrochow-app
```

## Old Droplet Checklist

Old Droplet:

```text
159.203.41.218
```

Check old app status:

```bash
ssh root@159.203.41.218 "systemctl status afrochow --no-pager"
```

Stop old app:

```bash
ssh root@159.203.41.218 "systemctl stop afrochow && systemctl status afrochow --no-pager"
```

Before destroying the old Droplet, confirm:

```text
api.afrochow.ca is healthy on new VPS
old app is stopped
database was migrated
uploads/media were copied if the old server stored local files
S3 backup exists after cutover
snapshot exists if rollback is still desired
```

Shutdown is not enough to stop DigitalOcean Droplet billing. Destroy the Droplet when the rollback window is over.
