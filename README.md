# Afrochow Backend

Afrochow Backend is a Spring Boot 4 / Java 21 API for the Afrochow marketplace. It serves the public API used by the Vercel frontend, handles authentication, vendors, customers, orders, payments, notifications, address geocoding, uploaded files, and event-driven background work.

## Current Production Status

Production has been cut over to the new VPS.

```text
Production API: https://api.afrochow.ca
Frontend app:   https://www.afrochow.ca
New VPS:        65.21.111.29
VPS app path:   /opt/afrochow
Old Droplet:    159.203.41.218
```

The old Droplet application has been stopped after migration. Keep it only as a short rollback window or snapshot source. Destroying the old Droplet is required to stop Droplet billing; shutting it down alone does not stop charges.

## Production Architecture

Production runs with Docker Compose on the VPS under `/opt/afrochow`.

```text
Cloudflare
  -> api.afrochow.ca
  -> Nginx container
  -> Spring Boot app container
  -> MySQL / Kafka / Redis containers
```

Core services:

```text
afrochow-nginx       Public HTTP/HTTPS entrypoint
afrochow-app         Spring Boot API on container port 8081
afrochow-mysql       MySQL 8.4 database
afrochow-kafka       Apache Kafka 4.1.2 single broker
afrochow-kafka-init  Topic creation job
afrochow-kafka-ui    Private Kafka UI, reachable only through SSH tunnel
afrochow-redis       Redis 7.4 cache/geospatial store
afrochow-db-backup   Scheduled MySQL backup worker to Amazon S3
```

Only Nginx is publicly exposed. MySQL, Kafka, Redis, and Kafka UI are bound to localhost or the private Docker network.

## HTTPS And DNS

Cloudflare is the public TLS proxy. Nginx is the origin proxy on the VPS.

Expected Cloudflare settings:

```text
DNS:              api.afrochow.ca -> 65.21.111.29, proxied
SSL/TLS mode:     Full (strict)
Origin cert path: /opt/afrochow/deploy/nginx/certs/cloudflare-origin.pem
Origin key path:  /opt/afrochow/deploy/nginx/certs/cloudflare-origin.key
```

The Cloudflare Origin Certificate is valid for the Afrochow API origin and should not be committed to git.

See [deploy/nginx/README.md](deploy/nginx/README.md) for the certificate and Nginx runbook.

## Frontend Integration

The production Vercel frontend is expected to call:

```text
NEXT_PUBLIC_API_URL=https://api.afrochow.ca/api
NEXT_PUBLIC_APP_URL=https://www.afrochow.ca
```

Backend CORS must include the exact frontend origins:

```env
APP_FRONTEND_URL=https://www.afrochow.ca
CORS_ALLOWED_ORIGINS=https://afrochow.ca,https://www.afrochow.ca
```

Because the backend uses credentialed CORS, do not use `*` for allowed origins.

Quick CORS check:

```bash
curl -i -X OPTIONS https://api.afrochow.ca/api/categories \
  -H "Origin: https://www.afrochow.ca" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization,content-type"
```

Expected header:

```text
access-control-allow-origin: https://www.afrochow.ca
```

## Kafka Event Pipeline

Afrochow uses a transactional outbox pattern. Domain events are written by the app and then published to Kafka.

Topics:

```text
afrochow.domain-events        Main event stream
afrochow.domain-events.retry  Reserved retry topic
afrochow.domain-events.dlq    Dead-letter topic for failed consumer records
```

Current consumer groups:

```text
afrochow-notification-service
afrochow-address-geocoding-service
afrochow-payment-transfer-service
```

One topic can safely serve multiple consumer groups. Each consumer group maintains its own offsets and receives its own logical copy of events.

Error handling:

```text
Consumer failure -> retry 3 times -> publish original record to afrochow.domain-events.dlq
Retry backoff: 1000 ms
```

Config keys:

```env
KAFKA_TOPIC_DOMAIN_EVENTS=afrochow.domain-events
KAFKA_TOPIC_DOMAIN_EVENTS_RETRY=afrochow.domain-events.retry
KAFKA_TOPIC_DOMAIN_EVENTS_DLQ=afrochow.domain-events.dlq
KAFKA_CONSUMER_RETRY_BACKOFF_MS=1000
KAFKA_CONSUMER_MAX_RETRY_ATTEMPTS=3
```

Kafka UI is intentionally private. Start an SSH tunnel from your Mac:

```bash
ssh -L 8088:127.0.0.1:8088 root@65.21.111.29
```

Then open:

```text
http://localhost:8088
```

## Database Backups

MySQL backups are handled by the `afrochow-db-backup` container.

Backup target:

```text
s3://afrochow-db-backups-prod-740204037801-ca-central-1-an/mysql/
```

Schedule:

```text
Daily at 2 AM America/Edmonton
```

Manual backup:

```bash
ssh root@65.21.111.29 "cd /opt/afrochow && docker compose --env-file .env.prod -f docker-compose.prod.yml run --rm -e BACKUP_ONESHOT=true db-backup"
```

See [deploy/backup/README.md](deploy/backup/README.md) for the S3 bucket, IAM, lifecycle, and restore notes.

## Local Development

The local development shape can run app services on the laptop while Kafka/Redis run in Docker.

Typical local dependencies:

```text
Spring Boot app: local
MySQL:           local laptop, port 3306
Kafka:           Docker, localhost:9092
Redis:           Docker, localhost:6379
```

Start Kafka and Redis locally:

```bash
docker compose -f docker-compose.prod.yml up -d kafka kafka-init redis
```

Check Kafka:

```bash
nc -vz localhost 9092
```

Check Redis:

```bash
docker exec -it afrochow-redis redis-cli -a "$REDIS_PASSWORD" ping
```

Build locally:

```bash
./mvnw -q -DskipTests compile
```

## Production Deploy Basics

Most production commands should run from the VPS:

```bash
ssh root@65.21.111.29
cd /opt/afrochow
```

Use the env file explicitly:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```

Restart only the app after env or code changes:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps --build --force-recreate app
```

Restart Nginx after Nginx config or cert changes:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d nginx
docker exec afrochow-nginx nginx -t
```

## Monitoring Runbook

Operational monitoring commands live in [docs/VPS_MONITORING_GLOSSARY.md](docs/VPS_MONITORING_GLOSSARY.md).

Start there for:

```text
API health checks
Docker service status
App logs
Nginx logs and TLS checks
MySQL health and backups
Kafka topics, consumer groups, lag, DLQ, and Kafka UI
Redis health
Disk, memory, CPU, and port checks
Old Droplet shutdown/destroy checklist
```

## Security Notes

Do not commit:

```text
.env
.env.prod
Cloudflare origin certificate key
AWS access keys
JWT secrets
Stripe secrets
Google OAuth secrets
Database dumps
```

Kafka UI is private through SSH tunnel only. Do not expose it publicly unless Cloudflare Access or another authentication layer is added first.
