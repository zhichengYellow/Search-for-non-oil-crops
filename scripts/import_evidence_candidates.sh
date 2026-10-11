#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${DB_URL:-}" || -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ]]; then
  echo "Set DB_URL, DB_USER and DB_PASSWORD before importing evidence candidates." >&2
  exit 2
fi

mode="--dry-run"
if [[ $# -gt 1 ]]; then
  echo "Usage: $0 [--dry-run|--commit]" >&2
  exit 2
fi
if [[ $# -eq 1 ]]; then
  case "$1" in
    --dry-run|--commit) mode="$1" ;;
    *) echo "Usage: $0 [--dry-run|--commit]" >&2; exit 2 ;;
  esac
fi

cd "$(dirname "$0")/.."
exec mvn -q -DskipTests compile exec:java \
  -Dexec.mainClass=org.nonfood.oilplants.util.EvidenceCandidateImportCli \
  -Dexec.classpathScope=runtime \
  -Dexec.args="$mode"
