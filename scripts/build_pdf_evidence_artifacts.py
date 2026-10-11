#!/usr/bin/env python3
"""Build review artifacts from the preserved PlantScope course-PDF extraction.

This program does not OCR the PDF, confirm biological facts, or write to MySQL.
It creates page/table evidence crops and exports explicitly unverified candidates.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from collections import defaultdict
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any

try:
    import fitz
except ImportError as exc:  # pragma: no cover - exercised by CLI in configured env
    raise SystemExit("PyMuPDF is required: python3 -m pip install pymupdf") from exc


COMPONENT_CODES = (
    "C12:0", "C14:0", "C16:0", "C16:1", "C18:0",
    "C18:1", "C18:2", "C18:3", "C20:0", "C20:1",
)

FA_CSV_FIELDS = [
    "candidate_id", "plant_id", "sample_id", "row_no",
    "collection_unit_candidate", "testing_unit_candidate",
    "sample_part_candidate", "origin_candidate", "component_code",
    "raw_value_text", "candidate_numeric_value", "unit_candidate",
    "candidate_type", "verification_status", "source_pdf_page",
    "source_printed_page", "evidence_bbox_top_origin_json",
    "evidence_image_reference", "source_record_reference", "qc_flags",
]

VERIFIED_CSV_FIELDS = [
    "plant_id", "sample_id", "component_code", "value", "unit",
    "source_pdf_page", "source_printed_page", "source_reference",
    "verification_status", "reviewed_by", "reviewed_at",
]


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def stable_id(*parts: object) -> str:
    payload = "\x1f".join(str(part) for part in parts).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def decimal_text(raw: str) -> str | None:
    try:
        value = Decimal(raw.strip())
    except (InvalidOperation, AttributeError):
        return None
    if not value.is_finite():
        return None
    return format(value, "f")


def top_origin_bbox(sample: dict[str, Any], candidate: dict[str, Any]) -> list[float] | None:
    bbox = candidate.get("source_bbox_normalized_top_origin")
    if bbox and len(bbox) == 4:
        return [float(value) for value in bbox]
    bbox = candidate.get("source_bbox_normalized")
    if bbox and len(bbox) == 4:
        x, y_bottom, width, height = map(float, bbox)
        return [x, 1.0 - y_bottom - height, width, height]
    return None


def collect_review_boxes(
    samples: list[dict[str, Any]], verified_oil: list[dict[str, Any]],
) -> dict[tuple[str, int], list[list[float]]]:
    """Return page-local row boxes, converting Vision's bottom-origin y values."""
    sample_by_id = {sample["sample_id"]: sample for sample in samples}
    samples_with_verified_oil_boxes: set[str] = set()
    grouped: dict[tuple[str, int], list[list[float]]] = defaultdict(list)
    for value in verified_oil:
        sample = sample_by_id.get(value.get("sample_id"))
        bbox = value.get("source_bbox_normalized")
        if sample and bbox and len(bbox) == 4:
            samples_with_verified_oil_boxes.add(sample["sample_id"])
            x, y_bottom, width, height = map(float, bbox)
            grouped[(sample["plant_id"], int(sample["source_pdf_page"]))].append(
                [x, 1.0 - y_bottom - height, width, height]
            )

    # The one unreadable oil cell on physical page 11 still has manually
    # transcribed fatty-acid cell coordinates; use those only to frame its row.
    for sample in samples:
        key = (sample["plant_id"], int(sample["source_pdf_page"]))
        if sample["sample_id"] in samples_with_verified_oil_boxes:
            continue
        for candidate in sample.get("fatty_acid_value_candidates") or []:
            bbox = top_origin_bbox(sample, candidate)
            if bbox:
                grouped[key].append(bbox)
    return grouped


def build_fatty_acid_candidate_rows(
    plants_data: dict[str, Any], samples_data: dict[str, Any], manual_data: dict[str, Any],
) -> list[dict[str, str]]:
    samples = samples_data["samples"]
    manual_rows = manual_data["rows"]
    page_samples = sorted(
        (s for s in samples if int(s.get("source_pdf_page", -1)) == 11),
        key=lambda sample: sample["sample_id"],
    )
    expected_hash = manual_data["source_pdf_sha256"]
    if samples_data.get("source_pdf_sha256") != expected_hash:
        raise ValueError("Manual transcription and sample JSON PDF hashes differ")
    if len(page_samples) != len(manual_rows):
        raise ValueError("PDF page 11 sample count does not match manual row transcription")
    if not page_samples:
        return []
    plant_ids = {sample["plant_id"] for sample in page_samples}
    if len(plant_ids) != 1:
        raise ValueError("PDF page 11 table rows map to more than one plant ID")
    plant_id = next(iter(plant_ids))
    plant_by_id = {plant["plant_id"]: plant for plant in plants_data["plants"]}
    plant = plant_by_id.get(plant_id)
    if not plant:
        raise ValueError(f"Plant ID missing from plants.json: {plant_id}")

    output: list[dict[str, str]] = []
    for row_no, (manual, sample) in enumerate(zip(manual_rows, page_samples), start=1):
        comparisons = {
            "collection_unit_candidate": manual.get("collection_unit"),
            "testing_unit_candidate": manual.get("testing_unit"),
            "sample_part_candidate": manual.get("sample_part"),
            "origin_candidate": manual.get("origin"),
        }
        for field, expected in comparisons.items():
            if sample.get(field) != expected:
                raise ValueError(f"Page 11 row {row_no} crosswalk mismatch: {field}")
        existing = {
            cell.get("component_name"): cell
            for cell in (sample.get("fatty_acid_value_candidates") or [])
        }
        numeric_values: list[Decimal] = []
        for component in COMPONENT_CODES:
            raw = manual.get("fatty_acids", {}).get(component)
            if raw is None or str(raw).strip() == "":
                continue
            raw = str(raw).strip()
            prior = existing.get(component)
            if not prior or str(prior.get("raw_value_text", "")).strip() != raw:
                raise ValueError(f"Page 11 row {row_no} {component} candidate mismatch")
            bbox = top_origin_bbox(sample, prior)
            if bbox is None:
                raise ValueError(f"Page 11 row {row_no} {component} has no cell bbox")
            numeric = decimal_text(raw)
            if numeric is not None:
                numeric_values.append(Decimal(numeric))
            output.append({
                "candidate_id": stable_id(expected_hash, sample["sample_id"], component, raw, bbox),
                "plant_id": plant_id,
                "sample_id": sample["sample_id"],
                "row_no": str(row_no),
                "collection_unit_candidate": str(manual.get("collection_unit") or ""),
                "testing_unit_candidate": str(manual.get("testing_unit") or ""),
                "sample_part_candidate": str(manual.get("sample_part") or ""),
                "origin_candidate": str(manual.get("origin") or ""),
                "component_code": component,
                "raw_value_text": raw,
                "candidate_numeric_value": numeric or "",
                "unit_candidate": "",
                "candidate_type": "MANUAL_ROW_TRANSCRIPTION_NOT_UNIT_VERIFIED",
                "verification_status": "REVIEW_PENDING",
                "source_pdf_page": str(sample["source_pdf_page"]),
                "source_printed_page": str(sample["source_printed_page"]),
                "evidence_bbox_top_origin_json": json.dumps(bbox, separators=(",", ":")),
                "evidence_image_reference": (
                    f"data/pdf_extract/table_crops/{plant_id}-pdf-page-11.jpg"
                ),
                "source_record_reference": "data/manual_table_rows.json",
                "qc_flags": "",
            })
        if numeric_values and sum(numeric_values) > Decimal("100"):
            flag = "OBSERVED_COMPONENT_CANDIDATE_SUM_OVER_100_RECHECK_CELLS"
            for record in output:
                if record["sample_id"] == sample["sample_id"]:
                    record["qc_flags"] = flag
    return output


def build_verified_rows(samples_data: dict[str, Any]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for sample in samples_data["samples"]:
        for component in sample.get("fatty_acid_components") or []:
            if component.get("verification_status") != "VERIFIED":
                continue
            unit = component.get("unit") or component.get("source_unit")
            value = component.get("percentage")
            if value is None or not unit:
                continue
            rows.append({
                "plant_id": sample["plant_id"],
                "sample_id": sample["sample_id"],
                "component_code": component.get("component_name", ""),
                "value": str(value),
                "unit": str(unit),
                "source_pdf_page": str(sample.get("source_pdf_page", "")),
                "source_printed_page": str(sample.get("source_printed_page", "")),
                "source_reference": component.get("source_reference", ""),
                "verification_status": "VERIFIED",
                "reviewed_by": component.get("reviewed_by", ""),
                "reviewed_at": component.get("reviewed_at", ""),
            })
    return rows


def write_csv(path: Path, columns: list[str], rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def export_unassigned_fragments(output_path: Path, samples: list[dict[str, Any]]) -> int:
    fields = [
        "plant_id", "sample_id", "source_pdf_page", "source_printed_page",
        "raw_fragment", "ocr_confidence", "bbox_bottom_origin_json",
        "classification", "review_status",
    ]
    rows: list[dict[str, str]] = []
    for sample in samples:
        for fragment in sample.get("unparsed_numeric_fragments") or []:
            bbox = fragment.get("bbox_normalized")
            rows.append({
                "plant_id": str(sample.get("plant_id", "")),
                "sample_id": str(sample.get("sample_id", "")),
                "source_pdf_page": str(sample.get("source_pdf_page", "")),
                "source_printed_page": str(sample.get("source_printed_page", "")),
                "raw_fragment": str(fragment.get("text", "")),
                "ocr_confidence": str(fragment.get("confidence", "")),
                "bbox_bottom_origin_json": json.dumps(bbox, separators=(",", ":")) if bbox else "",
                "classification": "UNASSIGNED_NUMERIC_FRAGMENT_NOT_FATTY_ACID",
                "review_status": "REVIEW_PENDING",
            })
    write_csv(output_path, fields, rows)
    return len(rows)


def inventory_pdf(pdf_path: Path, plants: list[dict[str, Any]], review_renders: Path) -> dict[str, Any]:
    doc = fitz.open(pdf_path)
    page_inventory = []
    for index, page in enumerate(doc, start=1):
        page_plants = [
            {"plant_id": plant["plant_id"], "name_zh": plant.get("name_zh"),
             "scientific_name": plant.get("scientific_name"),
             "entry_ordinal_on_start_page": plant.get("entry_ordinal_on_start_page")}
            for plant in plants if index in plant.get("source_pdf_pages", [])
        ]
        source_render = review_renders / f"page-{index:02d}.jpg"
        render_dimensions = None
        if source_render.exists():
            from PIL import Image
            with Image.open(source_render) as image:
                render_dimensions = list(image.size)
        page_inventory.append({
            "source_pdf_page": index,
            "source_printed_page": index + 10,
            "page_width_points": round(page.rect.width, 3),
            "page_height_points": round(page.rect.height, 3),
            "text_layer_characters": len(page.get_text().strip()),
            "embedded_image_count": len(page.get_images(full=True)),
            "scan_page": len(page.get_text().strip()) == 0 and bool(page.get_images(full=True)),
            "existing_review_render": str(source_render) if source_render.exists() else None,
            "existing_render_dimensions_px": render_dimensions,
            "visual_layout": "full-page raster with mixed text, photos and chemical tables; manual inspection required",
            "plant_entries_on_page": page_plants,
        })
    doc.close()
    return {
        "source_pdf": str(pdf_path),
        "source_pdf_sha256": sha256_file(pdf_path),
        "source_pdf_bytes": pdf_path.stat().st_size,
        "page_count": len(page_inventory),
        "page_numbering": "physical page 1..19; printed page number observed as physical page + 10",
        "text_layer_summary": {
            "pages_with_text": sum(page["text_layer_characters"] > 0 for page in page_inventory),
            "pages_without_text": sum(page["text_layer_characters"] == 0 for page in page_inventory),
        },
        "pages": page_inventory,
    }


def render_table_crops(
    pdf_path: Path,
    plants: list[dict[str, Any]],
    samples: list[dict[str, Any]],
    verified_oil: list[dict[str, Any]],
    output_dir: Path,
    dpi: int = 300,
) -> list[dict[str, Any]]:
    boxes_by_group = collect_review_boxes(samples, verified_oil)
    plant_by_id = {plant["plant_id"]: plant for plant in plants}
    samples_by_group: dict[tuple[str, int], list[dict[str, Any]]] = defaultdict(list)
    for sample in samples:
        samples_by_group[(sample["plant_id"], int(sample["source_pdf_page"]))].append(sample)
    doc = fitz.open(pdf_path)
    manifest: list[dict[str, Any]] = []
    matrix = fitz.Matrix(dpi / 72, dpi / 72)
    for (plant_id, pdf_page_no), boxes in sorted(boxes_by_group.items(), key=lambda entry: (entry[0][1], entry[0][0])):
        if not boxes:
            raise ValueError(f"No evidence bounding boxes for {plant_id} on PDF page {pdf_page_no}")
        page = doc[pdf_page_no - 1]
        min_y = min(box[1] for box in boxes)
        max_y = max(box[1] + box[3] for box in boxes)
        y0 = max(0.0, min_y - 0.035)
        y1 = min(1.0, max_y + 0.020)
        if y1 <= y0:
            raise ValueError(f"Invalid evidence crop range for {plant_id} page {pdf_page_no}")
        clip = fitz.Rect(
            page.rect.width * 0.01,
            page.rect.height * y0,
            page.rect.width * 0.99,
            page.rect.height * y1,
        )
        pixmap = page.get_pixmap(matrix=matrix, clip=clip, alpha=False, annots=False)
        crop_name = f"{plant_id}-pdf-page-{pdf_page_no:02d}.jpg"
        crop_path = output_dir / crop_name
        crop_path.parent.mkdir(parents=True, exist_ok=True)
        pixmap.save(str(crop_path), jpg_quality=94)
        plant = plant_by_id.get(plant_id, {})
        group_samples = sorted(samples_by_group[(plant_id, pdf_page_no)], key=lambda s: s["sample_id"])
        manifest.append({
            "evidence_image_reference": str(crop_path).replace("\\", "/"),
            "plant_id": plant_id,
            "plant_name_zh": plant.get("name_zh"),
            "plant_scientific_name_as_printed": plant.get("scientific_name"),
            "source_pdf_page": pdf_page_no,
            "source_printed_page": pdf_page_no + 10,
            "sample_ids": [sample["sample_id"] for sample in group_samples],
            "sample_row_count": len(group_samples),
            "crop_bbox_normalized_top_origin": [0.01, round(y0, 6), 0.98, round(y1 - y0, 6)],
            "render_dpi": dpi,
            "purpose": "table evidence crop based on existing row-cell coordinates; crop does not confirm species or numeric values",
            "review_status": "REVIEW_PENDING",
        })
    doc.close()
    return manifest


def build_artifacts(repo_root: Path, output_dir: Path, dpi: int = 300) -> dict[str, int]:
    pdf_path = repo_root / "docs/课程设计资料.pdf"
    data_dir = repo_root / "data"
    plants_data = read_json(data_dir / "plants.json")
    samples_data = read_json(data_dir / "oil_samples.json")
    manual_data = read_json(data_dir / "manual_table_rows.json")
    verification_data = read_json(data_dir / "oil_value_verification.json")
    if not pdf_path.exists():
        raise FileNotFoundError(f"Course PDF not found: {pdf_path}")
    pdf_hash = sha256_file(pdf_path)
    for label, payload in (("plants", plants_data), ("samples", samples_data), ("manual", manual_data), ("oil verification", verification_data)):
        recorded_hash = payload.get("source_pdf_sha256")
        if recorded_hash and recorded_hash != pdf_hash:
            raise ValueError(f"{label} source PDF hash does not match current PDF")

    plants = plants_data["plants"]
    samples = samples_data["samples"]
    manual_fa = build_fatty_acid_candidate_rows(plants_data, samples_data, manual_data)
    verified_fa = build_verified_rows(samples_data)
    write_csv(data_dir / "fatty_acid_candidates.csv", FA_CSV_FIELDS, manual_fa)
    write_csv(data_dir / "fatty_acid_verified.csv", VERIFIED_CSV_FIELDS, verified_fa)
    unassigned_count = export_unassigned_fragments(output_dir / "unassigned_numeric_fragments.csv", samples)

    inventory = inventory_pdf(pdf_path, plants, repo_root / "data/source_pages")
    inventory["manual_table_transcription"] = {
        "source_pdf_page": 11,
        "source_printed_page": 21,
        "row_count": len(manual_data["rows"]),
        "nonempty_fatty_acid_cell_candidates": len(manual_fa),
        "confirmed_fatty_acid_values": len(verified_fa),
        "unit_printed_for_fatty_acid_columns": False,
        "candidate_import_policy": "Do not import into the formal fatty_acid_components table until unit and each cell are reviewed.",
    }
    crops = render_table_crops(
        pdf_path, plants, samples, verification_data["verified_values"],
        output_dir / "table_crops", dpi,
    )
    inventory["table_crop_count"] = len(crops)
    inventory["table_crops"] = crops
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "pdf_inventory_and_crop_manifest.json").write_text(
        json.dumps(inventory, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (output_dir / "README.md").write_text(
        "# PDF evidence artifacts\n\n"
        "Generated by `scripts/build_pdf_evidence_artifacts.py`. Table crops are keyed by physical PDF page and plant ID. "
        "They are evidence-navigation aids, not confirmation of a numeric cell or plant identity. "
        "All fatty-acid candidates remain `REVIEW_PENDING`; the source table does not print a unit. "
        "Unassigned OCR fragments are explicitly not classified as fatty acids.\n",
        encoding="utf-8",
    )
    return {
        "pages": inventory["page_count"],
        "plant_table_crops": len(crops),
        "fatty_acid_candidate_cells": len(manual_fa),
        "verified_fatty_acid_rows": len(verified_fa),
        "unassigned_numeric_fragments": unassigned_count,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--dpi", type=int, default=300)
    args = parser.parse_args()
    output_dir = args.output_dir or (args.repo_root / "data/pdf_extract")
    result = build_artifacts(args.repo_root.resolve(), output_dir.resolve(), args.dpi)
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
