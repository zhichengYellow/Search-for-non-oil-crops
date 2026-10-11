#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 || ( "$2" != "--dry-run" && "$2" != "--commit" ) ]]; then
  echo "Usage: $0 <candidate-jsonl> --dry-run|--commit" >&2
  exit 2
fi
if [[ -z "${DB_URL:-}" || -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ]]; then
  echo "Set DB_URL, DB_USER and DB_PASSWORD before staging candidates." >&2
  exit 2
fi

input="$1"
mode="$2"
exec mvn -q -DskipTests compile exec:java \
  -Dexec.mainClass=org.nonfood.oilplants.util.ExternalImportCli \
  -Dexec.classpathScope=runtime \
  -Dexec.args="--input $input --provider GBIF --source-key gbif-species-api-v2 --source-name 'GBIF Species API v2' --source-url https://techdocs.gbif.org/en/openapi/v1/species $mode"
