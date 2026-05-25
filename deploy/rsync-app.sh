#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:-root@65.21.111.29:/opt/afrochow/}"

rsync -avz \
  --exclude='.DS_Store' \
  --exclude='*/.DS_Store' \
  --include='/.dockerignore' \
  --include='/.env.prod.example' \
  --include='/.mvn/***' \
  --include='/Dockerfile' \
  --include='/deploy/***' \
  --include='/docker-compose.prod.yml' \
  --include='/mvnw' \
  --include='/pom.xml' \
  --include='/src/***' \
  --exclude='*' \
  ./ "$TARGET"
