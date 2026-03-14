#!/usr/bin/env bash
# scripts/run-chroma-validation.sh
#
# Convenience script that spins up a local Chroma container, runs
# ChromaEmbeddingStoreIT, then tears the container down.
#
# Usage:
#   bash scripts/run-chroma-validation.sh
#
# Override the Chroma image or port:
#   CHROMA_VALIDATION_IMAGE=chromadb/chroma:latest \
#   CHROMA_VALIDATION_PORT=19000 \
#     bash scripts/run-chroma-validation.sh

set -euo pipefail

COMPOSE_FILE="docker/docker-compose.chroma-validation.yml"
CHROMA_URL="${CHROMA_VALIDATION_URL:-http://127.0.0.1:${CHROMA_VALIDATION_PORT:-18000}}"

cleanup() {
  echo "[chroma-validation] Stopping Chroma..."
  docker compose -f "$COMPOSE_FILE" down -v
}
trap cleanup EXIT

echo "[chroma-validation] Starting Chroma at $CHROMA_URL ..."
docker compose -f "$COMPOSE_FILE" up -d

# Wait for Chroma to become ready (up to 30 s)
for i in $(seq 1 30); do
  if curl -sf "${CHROMA_URL}/api/v1/heartbeat" >/dev/null 2>&1; then
    echo "[chroma-validation] Chroma is ready."
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "[chroma-validation] ERROR: Chroma did not start within 30 seconds." >&2
    exit 1
  fi
  sleep 1
done

echo "[chroma-validation] Running ChromaEmbeddingStoreIT..."
CHROMA_VALIDATION_ENABLED=true \
  CHROMA_VALIDATION_URL="$CHROMA_URL" \
  bash ./mvnw -q -Dtest=ChromaEmbeddingStoreIT test

echo "[chroma-validation] All checks passed."
