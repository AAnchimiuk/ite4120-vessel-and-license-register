#!/usr/bin/env bash
# Generate an interactive SchemaSpy HTML visualization for the `vessels` schema only.
# Requires the local PostgreSQL container (`ite4120-postgres-1`, with Liquibase migrations applied).
# Output: docs/schemaspy/index.html
# Usage: ./scripts/run-schemaspy.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT/docs/schemaspy"

if [ "$(docker inspect -f '{{.State.Running}}' ite4120-postgres-1 2>/dev/null || true)" != "true" ]; then
  echo "PostgreSQL container (ite4120-postgres-1) is not running."
  echo "Run ./scripts/run-backend.sh once first so Liquibase creates the vessels schema."
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
chmod 777 "$OUT_DIR"

echo "Running SchemaSpy for schema 'vessels'..."
docker run --rm \
  --network container:ite4120-postgres-1 \
  -v "$OUT_DIR:/output" \
  schemaspy/schemaspy:latest \
  -t pgsql11 \
  -host localhost \
  -port 5432 \
  -db animals \
  -u "${ANIMALS_DB_USER:-animals}" \
  -p "${ANIMALS_DB_PASSWORD:-animals}" \
  -s vessels

echo ""
echo "SchemaSpy report generated at: $OUT_DIR/index.html"
if command -v open >/dev/null 2>&1; then
  open "$OUT_DIR/index.html"
fi
