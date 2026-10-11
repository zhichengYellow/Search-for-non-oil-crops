#!/usr/bin/env python3
"""Resolve a curator-supplied taxon list to GBIF taxonomy candidates; never confirms or publishes facts."""
import argparse
import csv
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

API_ROOT = "https://api.gbif.org/v2/species/match"
USER_AGENT = "PlantScope/3.2 taxonomy candidate importer"
MAX_NAMES = 1000
MAX_RESPONSE = 256 * 1024


def fetch_match(name: str, timeout: float = 8.0, opener=urllib.request.urlopen):
    query = urllib.parse.urlencode({"scientificName": name, "strict": "true"})
    request = urllib.request.Request(f"{API_ROOT}?{query}", headers={"Accept": "application/json", "User-Agent": USER_AGENT})
    last_error = None
    for attempt in range(4):
        try:
            with opener(request, timeout=timeout) as response:
                if response.status == 429 or response.status >= 500:
                    last_error = RuntimeError(f"GBIF HTTP {response.status}")
                    if attempt < 3:
                        time.sleep(min(2 ** attempt, 8))
                        continue
                    raise last_error
                if response.status < 200 or response.status >= 300:
                    raise RuntimeError(f"GBIF HTTP {response.status}")
                payload = response.read(MAX_RESPONSE + 1)
                if len(payload) > MAX_RESPONSE:
                    raise RuntimeError("GBIF response exceeded 256 KiB")
                return json.loads(payload.decode("utf-8"))
        except urllib.error.HTTPError as exc:
            last_error = RuntimeError(f"GBIF HTTP {exc.code}")
            if exc.code == 429 or exc.code >= 500:
                if attempt < 3:
                    time.sleep(min(2 ** attempt, 8))
                    continue
            raise last_error from exc
        except (TimeoutError, urllib.error.URLError) as exc:
            last_error = exc
            if attempt < 3:
                time.sleep(min(2 ** attempt, 8))
                continue
            raise RuntimeError(f"GBIF request failed: {exc}") from exc
    raise RuntimeError(str(last_error or "GBIF request failed"))


def load_names(path: Path):
    with path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames or "scientific_name" not in reader.fieldnames:
            raise ValueError("CSV must include a scientific_name column")
        rows = []
        for line, row in enumerate(reader, start=2):
            name = (row.get("scientific_name") or "").strip()
            if not name:
                raise ValueError(f"Line {line}: scientific_name is required")
            if len(name) > 220:
                raise ValueError(f"Line {line}: scientific_name exceeds 220 characters")
            rows.append({key: (value or "").strip() for key, value in row.items() if key})
            rows[-1]["scientific_name"] = name
        if not rows or len(rows) > MAX_NAMES:
            raise ValueError(f"CSV must contain 1–{MAX_NAMES} names")
        return rows


def candidate_record(row, payload, retrieved_at):
    usage = payload.get("usage") or {}
    diagnostics = payload.get("diagnostics") or {}
    key = usage.get("key")
    rank = usage.get("rank")
    matched = usage.get("canonicalName") or usage.get("name")
    if rank in {"SPECIES", "SUBSPECIES", "VARIETY", "FORM"} and key:
        resolution = "MATCH_REVIEW_REQUIRED"
    else:
        resolution = "NEW_TAXON_CANDIDATE"
    source_url = f"https://api.gbif.org/v2/species/{urllib.parse.quote(str(key), safe='')}" if key else API_ROOT
    return {
        "record_kind": "TAXONOMY",
        "resolution": resolution,
        "name_zh": row.get("name_zh") or None,
        "scientific_name": row["scientific_name"],
        "accepted_name": matched,
        "authorship": usage.get("authorship"),
        "taxonomic_rank": rank,
        "family_name": usage.get("family"),
        "genus_name": usage.get("genus"),
        "source_namespace": "GBIF",
        "external_taxon_id": str(key) if key else None,
        "source_url": source_url,
        "license_code": "REVIEW_REQUIRED_PER_SOURCE_TERMS",
        "attribution": "GBIF Species API v2 name match; verify the underlying checklist, license and citation before publication.",
        "evidence_text": None,
        "retrieved_at": retrieved_at,
        "match_type": diagnostics.get("matchType"),
        "match_confidence": diagnostics.get("confidence"),
        "seed_source_name": row.get("seed_source_name") or None,
        "seed_source_url": row.get("seed_source_url") or None,
        "local_plant_id": row.get("local_plant_id") or None,
        "raw_gbif_response": payload,
    }


def write_checkpoint(path: Path, state):
    temp = path.with_name(path.name + ".tmp")
    temp.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temp, path)


def load_run_state(output: Path, input_hash: str, resume: bool, requested_batch_uuid: Optional[str]):
    state_path = output.with_name(output.name + ".state.json")
    if resume:
        if not state_path.is_file():
            raise ValueError(f"Resume checkpoint does not exist: {state_path}")
        state = json.loads(state_path.read_text(encoding="utf-8"))
        if state.get("input_sha256") != input_hash:
            raise ValueError("Resume input does not match the checkpoint manifest SHA-256")
        batch_uuid = state.get("batch_uuid")
        if requested_batch_uuid and requested_batch_uuid != batch_uuid:
            raise ValueError("--batch-uuid does not match the saved resume batch")
        if not batch_uuid:
            raise ValueError("Resume checkpoint has no batch UUID")
        try:
            if str(uuid.UUID(batch_uuid)) != batch_uuid:
                raise ValueError("Resume checkpoint batch UUID is not canonical")
        except (TypeError, ValueError) as exc:
            raise ValueError("Resume checkpoint has an invalid batch UUID") from exc
        records = {}
        if output.exists():
            for raw_line in output.read_text(encoding="utf-8").splitlines():
                try:
                    record = json.loads(raw_line)
                    source_line = int(record["source_line"])
                except (json.JSONDecodeError, KeyError, TypeError, ValueError):
                    # A process may have stopped during a final JSONL write. Keep
                    # complete matching rows and discard only the damaged row.
                    continue
                if record.get("source_manifest_sha256") != input_hash or record.get("batch_uuid") != batch_uuid:
                    raise ValueError("Existing output row does not match the resume checkpoint")
                if source_line in records:
                    raise ValueError("Resume output contains a duplicate source line")
                records[source_line] = json.dumps(record, ensure_ascii=False, separators=(",", ":"))
        temp = output.with_name(output.name + ".tmp")
        temp.write_text("".join(records[k] + "\n" for k in sorted(records)), encoding="utf-8")
        os.replace(temp, output)
        state.setdefault("errors", {})
        for source_line in records:
            state["errors"].pop(str(source_line), None)
        return state_path, state, records

    if output.exists():
        raise ValueError(f"Output already exists; choose a new path or use --resume: {output}")
    batch_uuid = requested_batch_uuid or str(uuid.uuid4())
    try:
        if str(uuid.UUID(batch_uuid)) != batch_uuid:
            raise ValueError("--batch-uuid must use canonical UUID format")
    except (TypeError, ValueError) as exc:
        raise ValueError("--batch-uuid is invalid") from exc
    state = {"input_sha256": input_hash, "batch_uuid": batch_uuid, "errors": {}}
    write_checkpoint(state_path, state)
    output.touch()
    return state_path, state, {}


def write_error_ledger(path: Optional[Path], state):
    if not path:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + ".tmp")
    with temp.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["line", "scientific_name", "error"])
        writer.writeheader()
        for _, row in sorted(state.get("errors", {}).items(), key=lambda item: int(item[0])):
            writer.writerow(row)
    os.replace(temp, path)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--errors", type=Path)
    parser.add_argument("--min-interval-seconds", type=float, default=5.0)
    parser.add_argument("--batch-uuid")
    parser.add_argument("--resume", action="store_true", help="resume incomplete requests from the saved checkpoint")
    args = parser.parse_args(argv)
    if args.min_interval_seconds < 0.5 or args.min_interval_seconds > 60:
        parser.error("--min-interval-seconds must be between 0.5 and 60")
    rows = load_names(args.input)
    input_hash = hashlib.sha256(args.input.read_bytes()).hexdigest()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    state_path, state, records = load_run_state(args.output, input_hash, args.resume, args.batch_uuid)
    batch_uuid = state["batch_uuid"]
    last_request = None
    for index, row in enumerate(rows):
        source_line = index + 2
        if source_line in records:
            continue
        if last_request is not None:
            delay = args.min_interval_seconds - (time.monotonic() - last_request)
            if delay > 0:
                time.sleep(delay)
        last_request = time.monotonic()
        retrieved = datetime.now(timezone.utc).isoformat()
        try:
            payload = fetch_match(row["scientific_name"])
            record = candidate_record(row, payload, retrieved)
            record["source_line"] = source_line
            record["batch_uuid"] = batch_uuid
            record["source_manifest_sha256"] = input_hash
            line = json.dumps(record, ensure_ascii=False, separators=(",", ":"))
            with args.output.open("a", encoding="utf-8", newline="\n") as output:
                output.write(line + "\n")
                output.flush()
                os.fsync(output.fileno())
            records[source_line] = line
            state["errors"].pop(str(source_line), None)
        except Exception as exc:  # Continue the batch; --resume retries this row later.
            state.setdefault("errors", {})[str(source_line)] = {
                "line": source_line,
                "scientific_name": row["scientific_name"],
                "error": str(exc)[:1000],
            }
        write_checkpoint(state_path, state)
        write_error_ledger(args.errors, state)
    temp_output = args.output.with_name(args.output.name + ".tmp")
    temp_output.write_text("".join(records[k] + "\n" for k in sorted(records)), encoding="utf-8")
    os.replace(temp_output, args.output)
    state["retrieved_count"] = len(records)
    write_checkpoint(state_path, state)
    write_error_ledger(args.errors, state)
    failures = len(state.get("errors", {}))
    print(f"input={len(rows)} candidates={len(records)} failures={failures} batch={batch_uuid} input_sha256={input_hash}")
    if not records:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
