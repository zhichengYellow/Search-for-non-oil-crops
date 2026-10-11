#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${DB_URL:-}" || -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ]]; then
  echo "Set DB_URL, DB_USER and DB_PASSWORD before exporting the live data-quality report." >&2
  exit 2
fi

cd "$(dirname "$0")/.."
exec mvn -q -DskipTests compile exec:java \
  -Dexec.mainClass=org.nonfood.oilplants.util.DataQualityReportCli \
  -Dexec.classpathScope=runtime
