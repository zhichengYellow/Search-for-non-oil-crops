#!/usr/bin/env python3
"""Generate a repeatable MySQL import script from the reviewed PDF JSON files."""
from __future__ import annotations

import hashlib
import json
import uuid
from decimal import Decimal
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
PLANTS_PATH = ROOT / "data/plants.json"
SAMPLES_PATH = ROOT / "data/oil_samples.json"
MANIFEST_PATH = ROOT / "data/images/manifest.json"
OUTPUT_PATH = ROOT / "database/import_pdf_course_data.sql"
SOURCE_PREFIX = "pdf19-"


def sql_text(value: str) -> str:
    """Encode UTF-8 bytes as a MySQL string without SQL-mode-sensitive escapes."""
    return f"CONVERT(X'{value.encode('utf-8').hex()}' USING utf8mb4)"


def sql_value(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, (float, Decimal)):
        return format(Decimal(str(value)), "f")
    return sql_text(str(value))


def sql_json(value: Any) -> str:
    if value is None:
        return "NULL"
    raw = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    return f"CAST({sql_text(raw)} AS JSON)"


def source_json(plant: dict[str, Any]) -> dict[str, Any]:
    return {
        "fields": plant.get("fields", {}),
        "taxonomy_verification": plant.get("taxonomy_verification", {}),
        "raw_source_snippets": plant.get("raw_source_snippets", []),
        "aliases": plant.get("aliases", []),
        "review_flags": plant.get("review_flags", []),
        "scientific_synonyms_raw": plant.get("scientific_synonyms_raw"),
        "bbox_coordinate_convention": (
            "OCR bbox_normalized uses x from left and y from bottom; "
            "convert to top-origin with y_top=1-y-height."
        ),
    }


def field_value(plant: dict[str, Any], name: str) -> Any:
    return (plant.get("fields", {}).get(name) or {}).get("value")


def db_scientific_status(status: str) -> str:
    if status == "VERIFIED_FROM_PRINTED_HEADING":
        return status
    return "NEEDS_REVIEW" if status == "NEEDS_REVIEW" else "UNVERIFIED"


def crop_stored_name(image_id: str) -> str:
    # Match UUID.nameUUIDFromBytes(("pdf19-" + image_id).getBytes(UTF_8)) in Java.
    digest = hashlib.md5((SOURCE_PREFIX + image_id).encode("utf-8")).digest()
    return f"{uuid.UUID(bytes=digest, version=3)}.jpg"


def plant_update_sql(plant: dict[str, Any], document: str) -> str:
    key = plant["source_key"]
    scientific = plant.get("scientific_name")
    assignments = {
        "source_key": f"COALESCE(p.source_key, {sql_value(key)})",
        "source_document": sql_value(document),
        "source_pdf_sha256": sql_value(plant["source_pdf_sha256"]),
        "source_printed_pages": sql_value(",".join(map(str, plant["source_printed_pages"]))),
        "scientific_name_status": sql_value(db_scientific_status(plant["scientific_name_status"])),
        "source_ocr_json": sql_json(source_json(plant)),
        "genus_id": "g.id",
        "name_zh": sql_value(plant["name_zh"]),
        "scientific_name": sql_value(scientific),
        "morphology": sql_value(field_value(plant, "morphology")),
        "distribution": sql_value(field_value(plant, "distribution")),
        "cultivation": sql_value(field_value(plant, "cultivation")),
        "uses": sql_value(field_value(plant, "uses")),
        "source_pages": sql_value(",".join(map(str, plant["source_pdf_pages"]))),
        "verification_status": sql_value("VERIFIED" if plant.get("verification_status") == "VERIFIED" else "NEEDS_REVIEW"),
        "is_public": "1",
    }
    set_clause = ",\n  ".join(f"p.{column} = {expression}" for column, expression in assignments.items())
    identity = f"p.source_key = {sql_value(key)}"
    if scientific:
        identity += f" OR (p.source_key IS NULL AND p.scientific_name = {sql_value(scientific)})"
    return (
        "UPDATE plants p\n"
        "JOIN plant_genera g ON g.genus_name_latin = " + sql_value(plant["genus_latin"]) + "\n"
        f"SET {set_clause}\n"
        f"WHERE {identity};"
    )


def plant_insert_sql(plant: dict[str, Any], document: str) -> str:
    key = plant["source_key"]
    scientific = plant.get("scientific_name")
    columns = [
        "source_key", "source_document", "source_pdf_sha256", "source_printed_pages",
        "scientific_name_status", "source_ocr_json", "genus_id", "name_zh", "scientific_name",
        "morphology", "distribution", "cultivation", "uses", "source_pages",
        "verification_status", "is_public",
    ]
    values = [
        sql_value(key), sql_value(document), sql_value(plant["source_pdf_sha256"]),
        sql_value(",".join(map(str, plant["source_printed_pages"]))),
        sql_value(db_scientific_status(plant["scientific_name_status"])), sql_json(source_json(plant)),
        "g.id", sql_value(plant["name_zh"]), sql_value(scientific),
        sql_value(field_value(plant, "morphology")), sql_value(field_value(plant, "distribution")),
        sql_value(field_value(plant, "cultivation")), sql_value(field_value(plant, "uses")),
        sql_value(",".join(map(str, plant["source_pdf_pages"]))),
        sql_value("VERIFIED" if plant.get("verification_status") == "VERIFIED" else "NEEDS_REVIEW"), "1",
    ]
    cols = ", ".join(columns)
    vals = ", ".join(values)
    guard = f"NOT EXISTS (SELECT 1 FROM plants WHERE source_key = {sql_value(key)})"
    if scientific:
        guard += f"\n  AND NOT EXISTS (SELECT 1 FROM plants WHERE scientific_name = {sql_value(scientific)})"
    return (
        f"INSERT INTO plants ({cols})\n"
        f"SELECT {vals}\n"
        "FROM plant_genera g\n"
        f"WHERE g.genus_name_latin = {sql_value(plant['genus_latin'])}\n"
        f"  AND {guard};"
    )


def sample_source_snippets(sample: dict[str, Any]) -> dict[str, Any]:
    return {
        "raw_snippets": sample.get("raw_snippets", []),
        "unparsed_numeric_fragments": sample.get("unparsed_numeric_fragments", []),
        "oil_content_candidate": sample.get("oil_content_candidate"),
        "oil_content_verification": sample.get("oil_content_verification"),
        "fatty_acid_value_candidates": sample.get("fatty_acid_value_candidates", []),
        "other_measurement_candidates": sample.get("other_measurement_candidates", []),
        "raw_table_cells": sample.get("raw_table_cells", []),
        "table_header_records": sample.get("table_header_records", []),
        "manual_row_transcription": bool(sample.get("manual_row_transcription", False)),
        "bbox_coordinate_convention": (
            "OCR bboxes use x from left and y from bottom; "
            "explicitly named top_origin bboxes use x/y from top-left."
        ),
    }


def sample_columns_and_values(sample: dict[str, Any]) -> tuple[list[str], list[str]]:
    oil = sample.get("oil_content_pct")
    oil_candidate = sample.get("oil_content_candidate") or {}
    oil_status = sample.get("oil_content_verification", {}).get("status")
    status = (
        "VERIFIED" if oil_status == "VERIFIED_FROM_SOURCE_IMAGE"
        else "MISSING_OR_UNREADABLE" if oil_status == "MISSING_OR_UNREADABLE"
        else "NEEDS_REVIEW"
    )
    page = int(sample["source_pdf_page"])
    values = [
        sql_value(sample["source_key"]), sql_value(sample["record_kind"]), "p.id",
        sql_value(sample.get("sample_label")), sql_value(oil), sql_value(oil_candidate.get("unit")),
        sql_value(oil_candidate.get("raw_text")), sql_value(status),
        sql_value(sample.get("collection_unit_candidate")), sql_value(sample.get("testing_unit_candidate")),
        sql_value(sample.get("sample_part_candidate")), sql_value(sample.get("origin_candidate")),
        sql_json(sample.get("table_headers", [])), sql_json(sample_source_snippets(sample)),
        sql_json(sample.get("other_measurements", [])), sql_value(page),
        sql_value(int(sample["source_printed_page"])), sql_value(sample.get("raw_table_record")),
        sql_value("OCR 表格行候选；原文/表头/坐标保存在 source_snippets；本记录整体仍待核验。"),
        sql_value(str(page)), sql_value("NEEDS_REVIEW"),
    ]
    columns = [
        "source_key", "record_kind", "plant_id", "sample_label", "oil_content_pct", "oil_content_unit",
        "oil_content_source_text", "oil_content_status", "collection_unit_candidate", "testing_unit_candidate",
        "sample_part_candidate", "origin_candidate", "table_headers", "source_snippets", "other_measurements",
        "source_pdf_page", "source_printed_page", "raw_source_text", "source_note", "source_pages", "verification_status",
    ]
    return columns, values


def sample_update_sql(sample: dict[str, Any]) -> str:
    columns, values = sample_columns_and_values(sample)
    assignments = ",\n  ".join(f"s.{column} = {value}" for column, value in zip(columns[1:], values[1:]))
    return (
        "UPDATE plant_oil_samples s\n"
        "JOIN plants p ON p.source_key = " + sql_value(sample["plant_id"]) + "\n"
        f"SET {assignments}\n"
        f"WHERE s.source_key = {sql_value(sample['source_key'])};"
    )


def sample_insert_sql(sample: dict[str, Any]) -> str:
    columns, values = sample_columns_and_values(sample)
    guard = f"NOT EXISTS (SELECT 1 FROM plant_oil_samples WHERE source_key = {sql_value(sample['source_key'])})"
    return (
        f"INSERT INTO plant_oil_samples ({', '.join(columns)})\n"
        f"SELECT {', '.join(values)}\n"
        "FROM plants p\n"
        f"WHERE p.source_key = {sql_value(sample['plant_id'])}\n"
        f"  AND {guard};"
    )


def load_and_validate() -> tuple[dict[str, Any], list[dict[str, Any]], list[dict[str, Any]]]:
    plants_doc = json.loads(PLANTS_PATH.read_text(encoding="utf-8"))
    samples_doc = json.loads(SAMPLES_PATH.read_text(encoding="utf-8"))
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    plants = plants_doc["plants"]
    samples = samples_doc["samples"]
    crops = manifest["crops"]
    plant_keys = [p["source_key"] for p in plants]
    sample_keys = [s["source_key"] for s in samples]
    if len(plant_keys) != len(set(plant_keys)) or len(sample_keys) != len(set(sample_keys)):
        raise ValueError("Duplicate plant or sample source_key in JSON inputs")
    plant_key_set = set(plant_keys)
    if any(s.get("plant_id") not in plant_key_set for s in samples):
        raise ValueError("A sample references a plant that is absent from plants.json")
    if any(c.get("plant_id") not in plant_key_set for c in crops):
        raise ValueError("An image crop references a plant that is absent from plants.json")
    primary_crops = {}
    for crop in crops:
        if crop.get("is_primary"):
            primary_crops[crop["plant_id"]] = primary_crops.get(crop["plant_id"], 0) + 1
    if any(count > 1 for count in primary_crops.values()):
        raise ValueError("A plant has more than one source crop marked primary")
    if any(s.get("oil_content_pct") is not None and s.get("oil_content_verification", {}).get("status") != "VERIFIED_FROM_SOURCE_IMAGE" for s in samples):
        raise ValueError("Refusing to export an oil value without source-image verification")
    for sample in samples:
        for component in sample.get("fatty_acid_components", []):
            if component.get("verification_status") in ("VERIFIED", "VERIFIED_FROM_SOURCE_IMAGE") and component.get("percentage") is None:
                raise ValueError("A verified fatty-acid component must have an explicit numeric value")
    return plants_doc, plants, samples


def build_sql(plants_doc: dict[str, Any], plants: list[dict[str, Any]], samples: list[dict[str, Any]]) -> str:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    crops = manifest["crops"]
    families = {}
    genera = {}
    for plant in plants:
        families[plant["family_zh"]] = plant["family_latin"]
        genera[plant["genus_latin"]] = (plant["family_zh"], plant["genus_zh"])
    verified_oil = sum(s.get("oil_content_pct") is not None for s in samples)
    verified_components = sum(
        1 for s in samples for c in s.get("fatty_acid_components", [])
        if c.get("verification_status") in ("VERIFIED", "VERIFIED_FROM_SOURCE_IMAGE") and c.get("percentage") is not None
    )
    sample_indexes = sorted({0, len(plants) // 2, len(plants) - 1}) if plants else []
    sample_keys = [plants[index]["source_key"] for index in sample_indexes]
    output = [
        "-- Generated by scripts/build_pdf_import_sql.py; do not hand-edit.",
        f"-- Source PDF SHA-256: {plants_doc['source_pdf_sha256']}",
        f"-- Source rows: plants={len(plants)}, samples={len(samples)}, crops={len(crops)}, verified_oil={verified_oil}, verified_fatty_acids={verified_components}",
        "-- Repeatable by source_key. Missing measurements are SQL NULL; unverified fatty acids remain in source_snippets.",
        "-- Physical crop files must be staged with scripts/stage_pdf_crop_images.py before serving the imported image rows.",
        "SET NAMES utf8mb4;",
        "USE nonfood_oil_plants;",
        "START TRANSACTION;",
        "",
        "-- Upsert families and genera.",
    ]
    for family_zh, family_latin in sorted(families.items()):
        output.append(
            "INSERT INTO plant_families (family_name_zh, family_name_latin) VALUES "
            f"({sql_value(family_zh)}, {sql_value(family_latin)}) "
            "ON DUPLICATE KEY UPDATE family_name_latin = VALUES(family_name_latin);"
        )
    for genus_latin, (family_zh, genus_zh) in sorted(genera.items()):
        output.append(
            "INSERT INTO plant_genera (family_id, genus_name_zh, genus_name_latin) "
            f"SELECT f.id, {sql_value(genus_zh)}, {sql_value(genus_latin)} FROM plant_families f "
            f"WHERE f.family_name_zh = {sql_value(family_zh)} "
            "ON DUPLICATE KEY UPDATE family_id = VALUES(family_id), genus_name_zh = VALUES(genus_name_zh);"
        )
    output.extend(["", "-- Upsert plants by stable source_key; an existing legacy scientific-name row is adopted only when it has no source_key."])
    for plant in plants:
        output.append(plant_update_sql(plant, plants_doc["source_pdf"]))
        output.append(plant_insert_sql(plant, plants_doc["source_pdf"]))
    output.extend(["", "-- Reset this source's old primary flags while preserving any existing primary image from other records."])
    for plant in plants:
        output.append(
            "UPDATE plant_images SET is_primary = 0 WHERE plant_id = "
            f"(SELECT id FROM plants WHERE source_key = {sql_value(plant['source_key'])}) AND source_key IS NOT NULL;"
        )
    output.extend([
        "CREATE TEMPORARY TABLE _pdf_course_existing_primaries (plant_id BIGINT PRIMARY KEY) ENGINE=InnoDB;",
        "INSERT IGNORE INTO _pdf_course_existing_primaries (plant_id) SELECT DISTINCT plant_id FROM plant_images WHERE is_primary = 1;",
    ])
    output.extend(["", "-- Upsert aliases."])
    for plant in plants:
        for alias in plant.get("aliases", []):
            status = "VERIFIED" if alias.get("source_status") == "VERIFIED" else "NEEDS_REVIEW"
            output.append(
                "INSERT INTO plant_aliases (plant_id, alias, language, source_status) "
                f"SELECT p.id, {sql_value(alias['value'])}, 'zh', {sql_value(status)} FROM plants p "
                f"WHERE p.source_key = {sql_value(plant['source_key'])} "
                "ON DUPLICATE KEY UPDATE source_status = VALUES(source_status);"
            )
    output.extend(["", "-- Upsert image metadata. Crop files are copied separately by stage_pdf_crop_images.py."])
    for crop in crops:
        source_key = crop["image_id"]
        stored_name = crop_stored_name(source_key)
        source_note = (
            f"来源: {plants_doc['source_pdf']}；PDF第{crop['source_pdf_page']}页；"
            f"裁切框={json.dumps(crop['source_crop_bbox_normalized'], ensure_ascii=False, separators=(',', ':'))}"
        )
        verification = "VERIFIED" if crop.get("verification_status") == "VERIFIED_SOURCE_CROP" else "NEEDS_REVIEW"
        values = [
            sql_value(source_key), "p.id", sql_value(stored_name), sql_value(crop.get("alt_text")),
            sql_value(source_note), sql_value(crop["source_pdf_page"]), sql_value(crop["source_printed_page"]),
            sql_json(crop["source_crop_bbox_normalized"]), sql_value(verification),
            f"IF({1 if crop.get('is_primary', False) else 0} = 1 AND ep.plant_id IS NULL, 1, 0)",
        ]
        columns = ["source_key", "plant_id", "stored_name", "alt_text", "source_note", "source_pdf_page", "source_printed_page", "source_crop_bbox", "verification_status", "is_primary"]
        output.append(
            "UPDATE plant_images im JOIN plants p ON p.source_key = " + sql_value(crop["plant_id"]) + " "
            "LEFT JOIN _pdf_course_existing_primaries ep ON ep.plant_id = p.id "
            f"SET im.plant_id = p.id, im.stored_name = {sql_value(stored_name)}, im.alt_text = {sql_value(crop.get('alt_text'))}, "
            f"im.source_note = {sql_value(source_note)}, im.source_pdf_page = {crop['source_pdf_page']}, "
            f"im.source_printed_page = {crop['source_printed_page']}, im.source_crop_bbox = {sql_json(crop['source_crop_bbox_normalized'])}, "
            f"im.verification_status = {sql_value(verification)}, im.is_primary = IF({1 if crop.get('is_primary', False) else 0} = 1 AND ep.plant_id IS NULL, 1, 0) "
            f"WHERE im.source_key = {sql_value(source_key)};"
        )
        output.append(
            f"INSERT INTO plant_images ({', '.join(columns)}) "
            f"SELECT {', '.join(values)} FROM plants p LEFT JOIN _pdf_course_existing_primaries ep ON ep.plant_id = p.id "
            f"WHERE p.source_key = {sql_value(crop['plant_id'])} "
            f"AND NOT EXISTS (SELECT 1 FROM plant_images WHERE source_key = {sql_value(source_key)});"
        )
    output.append("DROP TEMPORARY TABLE _pdf_course_existing_primaries;")
    output.extend(["", "-- Upsert all source sample rows; unverified/missing measurements remain NULL."])
    for sample in samples:
        output.append(sample_update_sql(sample))
        output.append(sample_insert_sql(sample))
    output.extend(["", "-- Import only explicitly source-image-verified fatty-acid measurements."])
    for sample in samples:
        for component in sample.get("fatty_acid_components", []):
            if component.get("verification_status") not in ("VERIFIED", "VERIFIED_FROM_SOURCE_IMAGE"):
                continue
            component_values = [
                "s.id", sql_value(component["component_name"]), sql_value(component.get("percentage")),
                sql_value(sample["source_pdf_page"]), sql_value(component.get("unit")),
                sql_value(component.get("raw_value_text")), sql_json(component.get("source_bbox_normalized")),
                sql_value(f"PDF source page {sample['source_pdf_page']}"), sql_value("VERIFIED"),
            ]
            output.append(
                "INSERT INTO fatty_acid_components (sample_id, component_name, percentage, source_pdf_page, source_unit, raw_value_text, source_bbox, source_note, verification_status) "
                f"SELECT {', '.join(component_values)} FROM plant_oil_samples s WHERE s.source_key = {sql_value(sample['source_key'])} "
                "ON DUPLICATE KEY UPDATE percentage = VALUES(percentage), source_pdf_page = VALUES(source_pdf_page), "
                "source_unit = VALUES(source_unit), raw_value_text = VALUES(raw_value_text), source_bbox = VALUES(source_bbox), "
                "source_note = VALUES(source_note), verification_status = VALUES(verification_status);"
            )
    output.extend([
        "",
        "-- Enforce expected source totals before committing the import transaction.",
        "CREATE TEMPORARY TABLE _pdf_course_import_assertion (passed TINYINT NOT NULL CHECK (passed = 1)) ENGINE=InnoDB;",
        "INSERT INTO _pdf_course_import_assertion VALUES (IF(",
        f"  (SELECT COUNT(*) FROM plants WHERE source_key LIKE 'pdf19-%') = {len(plants)}",
        f"  AND (SELECT COUNT(*) FROM plant_oil_samples WHERE source_key LIKE 'pdf19-%') = {len(samples)}",
        f"  AND (SELECT COUNT(*) FROM plant_images WHERE source_key LIKE 'pdf19-%') = {len(crops)}",
        f"  AND (SELECT COUNT(*) FROM plant_oil_samples WHERE source_key LIKE 'pdf19-%' AND oil_content_status = 'VERIFIED' AND oil_content_pct IS NOT NULL) = {verified_oil}",
        f"  AND (SELECT COUNT(*) FROM fatty_acid_components fc JOIN plant_oil_samples s ON s.id = fc.sample_id WHERE s.source_key LIKE 'pdf19-%' AND fc.verification_status = 'VERIFIED') = {verified_components}, 1, 0));",
        "DROP TEMPORARY TABLE _pdf_course_import_assertion;",
        "COMMIT;",
        "SELECT",
        "  (SELECT COUNT(*) FROM plants WHERE source_key LIKE 'pdf19-%') AS source_plants,",
        "  (SELECT COUNT(*) FROM plant_oil_samples WHERE source_key LIKE 'pdf19-%') AS source_sample_rows,",
        "  (SELECT COUNT(*) FROM plant_images WHERE source_key LIKE 'pdf19-%') AS source_image_rows,",
        "  (SELECT COUNT(*) FROM plant_oil_samples WHERE source_key LIKE 'pdf19-%' AND oil_content_status = 'VERIFIED' AND oil_content_pct IS NOT NULL) AS verified_oil_rows,",
        "  (SELECT COUNT(*) FROM fatty_acid_components fc JOIN plant_oil_samples s ON s.id = fc.sample_id WHERE s.source_key LIKE 'pdf19-%' AND fc.verification_status = 'VERIFIED') AS verified_fatty_acid_rows;",
        "",
    ])
    plant_by_key = {plant["source_key"]: plant for plant in plants}
    for key in sample_keys:
        plant = plant_by_key[key]
        expected_pages = ",".join(map(str, plant["source_pdf_pages"]))
        output.extend([
            "SELECT p.source_key,",
            f"  {sql_value(plant['name_zh'])} AS expected_name_zh, p.name_zh AS database_name_zh, (p.name_zh <=> {sql_value(plant['name_zh'])}) AS name_zh_matches,",
            f"  {sql_value(plant.get('scientific_name'))} AS expected_scientific_name, p.scientific_name AS database_scientific_name, (p.scientific_name <=> {sql_value(plant.get('scientific_name'))}) AS scientific_name_matches,",
            f"  {sql_value(plant['family_zh'])} AS expected_family_zh, f.family_name_zh AS database_family_zh, (f.family_name_zh <=> {sql_value(plant['family_zh'])}) AS family_matches,",
            f"  {sql_value(plant['genus_zh'])} AS expected_genus_zh, g.genus_name_zh AS database_genus_zh, (g.genus_name_zh <=> {sql_value(plant['genus_zh'])}) AS genus_matches,",
            f"  {sql_value(expected_pages)} AS expected_source_pages, p.source_pages AS database_source_pages, (p.source_pages <=> {sql_value(expected_pages)}) AS source_pages_match,",
            "  (SELECT COUNT(*) FROM plant_images i WHERE i.plant_id = p.id AND i.source_key IS NOT NULL) AS image_rows,",
            "  (SELECT COUNT(*) FROM plant_oil_samples s WHERE s.plant_id = p.id AND s.oil_content_status = 'VERIFIED') AS verified_oil_rows",
            "FROM plants p LEFT JOIN plant_genera g ON g.id = p.genus_id LEFT JOIN plant_families f ON f.id = g.family_id",
            f"WHERE p.source_key = {sql_value(key)};",
        ])
    return "\n".join(output)


def main() -> None:
    plants_doc, plants, samples = load_and_validate()
    sql = build_sql(plants_doc, plants, samples)
    OUTPUT_PATH.write_text(sql, encoding="utf-8")
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    print(
        f"wrote {OUTPUT_PATH.relative_to(ROOT)}; plants={len(plants)} "
        f"samples={len(samples)} crops={len(manifest['crops'])} bytes={OUTPUT_PATH.stat().st_size}"
    )


if __name__ == "__main__":
    main()
