#!/usr/bin/env python3
"""Copy the PDF-derived crop images to UPLOAD_DIR using the SQL/Java importer names."""
from __future__ import annotations

import hashlib
import os
import shutil
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_PREFIX = "pdf19-"


def stored_name(image_id: str) -> str:
    digest = hashlib.md5((SOURCE_PREFIX + image_id).encode("utf-8")).digest()
    return f"{uuid.UUID(bytes=digest, version=3)}.jpg"


def main() -> None:
    import json

    manifest = json.loads((ROOT / "data/images/manifest.json").read_text(encoding="utf-8"))
    upload_dir = Path(os.environ.get("UPLOAD_DIR", Path(os.getenv("TMPDIR", "/tmp")) / "oil-plants-uploads")).expanduser().resolve()
    upload_dir.mkdir(parents=True, exist_ok=True)
    copied = 0
    for crop in manifest["crops"]:
        source = (ROOT / crop["path"]).resolve()
        if not source.is_file() or ROOT not in source.parents:
            raise FileNotFoundError(f"Invalid/missing crop source: {source}")
        shutil.copyfile(source, upload_dir / stored_name(crop["image_id"]))
        copied += 1
    print(f"staged {copied} crop files in {upload_dir}")


if __name__ == "__main__":
    main()
