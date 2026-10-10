#!/bin/sh
set -eu

if [ -z "${DB_URL:-}" ] || [ -z "${DB_USER:-}" ] || [ -z "${DB_PASSWORD:-}" ]; then
  echo "Set DB_URL, DB_USER and DB_PASSWORD before importing." >&2
  python3 - <<'PY'
import json
from datetime import datetime, timezone
from pathlib import Path
Path("data/database_import_attempt.json").write_text(json.dumps({
    "status": "BLOCKED_DB_CONFIGURATION_MISSING",
    "attempted_at_utc": datetime.now(timezone.utc).isoformat(),
    "reason": "DB_URL, DB_USER and/or DB_PASSWORD are unset; no database connection was made.",
    "imported_count": None
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
  exit 2
fi

if [ ! -f data/plants.json ] || [ ! -f data/oil_samples.json ]; then
  echo "Missing extraction files. Run scripts/build_pdf_data.py first." >&2
  exit 2
fi

if ! mvn -q -DskipTests compile exec:java \
  -Dexec.mainClass=org.nonfood.oilplants.util.PdfDataImportCli \
  -Dexec.classpathScope=runtime; then
  python3 - <<'PY'
import json
from datetime import datetime, timezone
from pathlib import Path
Path("data/database_import_attempt.json").write_text(json.dumps({
    "status": "FAILED_DATABASE_IMPORT",
    "attempted_at_utc": datetime.now(timezone.utc).isoformat(),
    "reason": "Importer returned an error; inspect the command output. No successful audit is available.",
    "imported_count": None
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
  exit 1
fi

rm -f data/database_import_attempt.json
