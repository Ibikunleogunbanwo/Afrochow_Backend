# Nginx and Cloudflare TLS

Production uses Cloudflare as the public TLS proxy and Nginx as the origin proxy.

Recommended Cloudflare mode:

```text
SSL/TLS encryption mode: Full (strict)
DNS record: api.afrochow.ca proxied
Origin cert: Cloudflare Origin Certificate installed on the VPS
```

## Origin Certificate Files

Create a Cloudflare Origin Certificate for:

```text
api.afrochow.ca
```

Place the files on the VPS:

```text
/opt/afrochow/deploy/nginx/certs/cloudflare-origin.pem
/opt/afrochow/deploy/nginx/certs/cloudflare-origin.key
```

Protect the key:

```bash
chmod 600 /opt/afrochow/deploy/nginx/certs/cloudflare-origin.key
```

Do not commit certificate files. The repo ignores everything in `deploy/nginx/certs/` except `.gitkeep`.

## Deploy

From the laptop:

```bash
cd /Users/ayoogunbanwo/software-project/Afrochow-Backend
rsync -avz docker-compose.prod.yml root@65.21.111.29:/opt/afrochow/docker-compose.prod.yml
rsync -avz deploy/nginx/ root@65.21.111.29:/opt/afrochow/deploy/nginx/
```

On the VPS, after installing the cert files:

```bash
cd /opt/afrochow
docker compose --env-file .env.prod -f docker-compose.prod.yml config --quiet
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d nginx
docker exec afrochow-nginx nginx -t
curl -kfsS https://127.0.0.1/api/actuator/health
```

Expected health response:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```
