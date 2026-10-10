#!/usr/bin/env python3
"""Build report statistics only from PDF inspection and extraction artifacts."""
from __future__ import annotations

import collections
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PDF = ROOT / "docs" / "课程设计资料.pdf"


def command(*args: str) -> str:
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout


def write_json(path: Path, obj: object) -> None:
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    plants_doc = json.loads((ROOT / "data/plants.json").read_text(encoding="utf-8"))
    samples_doc = json.loads((ROOT / "data/oil_samples.json").read_text(encoding="utf-8"))
    ocr = json.loads((ROOT / "data/ocr/vision_ocr.json").read_text(encoding="utf-8"))
    manifest = json.loads((ROOT / "data/images/manifest.json").read_text(encoding="utf-8"))
    plants = plants_doc["plants"]
    samples = samples_doc["samples"]

    info = command("pdfinfo", str(PDF))
    page_count = int(re.search(r"^Pages:\s+(\d+)", info, re.M).group(1))
    pdf_text = command("pdftotext", str(PDF), "-")
    nonspace_text_chars = len(re.sub(r"\s+", "", pdf_text))
    image_lines = [line for line in command("pdfimages", "-list", str(PDF)).splitlines() if re.match(r"\s*\d+\s+\d+\s+image\s", line)]
    embedded_image_properties = []
    for line in image_lines:
        parts = line.split()
        embedded_image_properties.append({"pdf_page": int(parts[0]), "width_px": int(parts[3]), "height_px": int(parts[4]), "x_ppi": int(parts[12]), "y_ppi": int(parts[13])})
    image_pages = collections.Counter(int(line.split()[0]) for line in image_lines)
    pdf_sha = hashlib.sha256(PDF.read_bytes()).hexdigest()

    field_statuses = collections.Counter()
    missing_by_field = collections.Counter()
    needs_review_by_field = collections.Counter()
    for plant in plants:
        for field, item in plant["fields"].items():
            field_statuses[f"{field}:{item['status']}"] += 1
            if item["status"] == "MISSING_OR_UNREADABLE":
                missing_by_field[field] += 1
            elif item["status"] == "NEEDS_REVIEW":
                needs_review_by_field[field] += 1

    oil_values = [s for s in samples if s.get("oil_content_verification", {}).get("status") == "VERIFIED_FROM_SOURCE_IMAGE" and s.get("oil_content_pct") is not None]
    rows_with_candidate = [s for s in samples if s.get("oil_content_candidate")]
    rows_without_oil = [s for s in samples if not s.get("oil_content_candidate")]
    rows_without_oil_by_page = collections.Counter(int(s["source_pdf_page"]) for s in rows_without_oil)
    candidate_rows_by_page = collections.Counter(int(s["source_pdf_page"]) for s in samples)
    partial_table_pages = sorted(rows_without_oil_by_page)
    manual_rows = [s for s in samples if s.get("manual_row_transcription")]
    manual_fa_candidates = sum(len(s.get("fatty_acid_value_candidates", [])) for s in samples)
    manual_other_candidates = sum(len(s.get("other_measurement_candidates", [])) for s in samples)
    image_crops = manifest["crops"]
    plants_with_images = {x["plant_id"] for x in image_crops}
    taxonomy_status_counts = collections.Counter(
        f"{field}:{status}"
        for plant in plants
        for field, status in plant.get("taxonomy_verification", {}).items()
    )

    audit_path = ROOT / "data/database_import_audit.json"
    attempt_path = ROOT / "data/database_import_attempt.json"
    db_audit = json.loads(audit_path.read_text(encoding="utf-8")) if audit_path.exists() else None
    db_attempt = json.loads(attempt_path.read_text(encoding="utf-8")) if attempt_path.exists() else None
    db_status = (db_audit or db_attempt or {}).get("status", "NOT_ATTEMPTED")

    report = {
        "generated_from": ["docs/课程设计资料.pdf", "data/plants.json", "data/oil_samples.json", "data/oil_value_verification.json", "data/manual_table_rows.json", "data/ocr/vision_ocr.json", "data/images/manifest.json"],
        "source_pdf": {
            "path": "docs/课程设计资料.pdf",
            "sha256": pdf_sha,
            "page_count": page_count,
            "meaningful_text_layer": nonspace_text_chars > 0,
            "pdftotext_nonspace_characters": nonspace_text_chars,
            "embedded_page_images": len(image_lines),
            "embedded_image_properties": embedded_image_properties,
            "pages_with_one_full_page_image": sorted(p for p, count in image_pages.items() if count == 1),
            "ocr_pages_processed": len(ocr),
            "rendered_review_images": len(list((ROOT / "data/source_pages").glob("page-*.jpg"))),
            "layout_notes": ["每页为一张约 300 DPI 的扁平扫描图，无可用文字层。", "版面混合单栏与双栏；部分页面同页包含两个植物条目。", "银杏条目跨 PDF 第 2–3 页。", "PDF 页码比书内印刷页码少 10。"],
        },
        "extraction": {
            "plant_entry_count": len(plants),
            "plants_with_chinese_name": sum(bool(x.get("name_zh")) for x in plants),
            "plants_with_latin_name": sum(bool(x.get("scientific_name")) for x in plants),
            "plants_with_family_zh_and_latin": sum(bool(x.get("family_zh") and x.get("family_latin")) for x in plants),
            "plants_with_genus_zh_and_latin": sum(bool(x.get("genus_zh") and x.get("genus_latin")) for x in plants),
            "alias_count": sum(len(x.get("aliases", [])) for x in plants),
            "aliases_needing_review": sum(1 for x in plants for a in x.get("aliases", []) if a.get("source_status") != "VERIFIED"),
            "entries_with_raw_synonym_notes": sum(bool(x.get("scientific_synonyms_raw")) for x in plants),
            "scientific_names_verified_from_heading": sum(x.get("scientific_name_status") == "VERIFIED_FROM_PRINTED_HEADING" for x in plants),
            "scientific_names_need_review": sum(x.get("scientific_name_status") == "NEEDS_REVIEW" for x in plants),
            "table_header_records": sum(bool(x.get("table_headers")) for x in plants),
            "raw_ocr_source_snippets": sum(len(x.get("raw_source_snippets", [])) for x in plants),
            "plant_fields_needing_review": sum(needs_review_by_field.values()),
            "plant_fields_missing_or_unreadable": sum(missing_by_field.values()),
            "field_status_counts": dict(sorted(field_statuses.items())),
            "missing_field_counts": dict(sorted(missing_by_field.items())),
            "fields_needing_review_counts": dict(sorted(needs_review_by_field.items())),
            "taxonomy_status_counts": dict(sorted(taxonomy_status_counts.items())),
            "plants_with_missing_or_unreadable_fields": [
                {"plant_id": p["plant_id"], "name_zh": p["name_zh"], "pdf_pages": p["source_pdf_pages"], "missing_fields": p["missing_fields"]}
                for p in plants if p["missing_fields"]
            ],
            "plants_with_taxonomy_review_flags": [
                {"plant_id": p["plant_id"], "name_zh": p["name_zh"], "pdf_pages": p["source_pdf_pages"], "scientific_name": p["scientific_name"], "review_flags": p["review_flags"]}
                for p in plants if p.get("scientific_name_status") == "NEEDS_REVIEW" or p.get("scientific_synonyms_raw")
            ],
        },
        "images": {
            "crop_count": len(image_crops),
            "plants_with_associated_crops": len(plants_with_images),
            "total_plants": len(plants),
            "all_crop_associations_verified": all(x.get("verification_status") == "VERIFIED_SOURCE_CROP" for x in image_crops),
            "source_page_metadata_present": all(x.get("source_pdf_page") and x.get("source_crop_bbox_normalized") for x in image_crops),
            "crop_archive": "data/images/crops/",
            "manifest": "data/images/manifest.json",
        },
        "chemistry": {
            "ocr_row_candidate_count": len(samples),
            "row_candidates_linked_to_plant": sum(bool(s.get("plant_id")) and s["plant_id"] in {p["plant_id"] for p in plants} for s in samples),
            "row_candidates_with_visually_verified_oil_content": len(oil_values),
            "row_candidates_without_oil_content_candidate": len(rows_without_oil),
            "manual_page_checked_table_rows": len(manual_rows),
            "manual_page_checked_rows_by_pdf_page": dict(sorted(collections.Counter(int(s["source_pdf_page"]) for s in manual_rows).items())),
            "pages_with_oil_cell_candidates": sorted(p for p in candidate_rows_by_page if any(s.get("source_pdf_page") == p and s.get("oil_content_candidate") for s in samples)),
            "pages_with_some_rows_missing_oil_cells": [{"pdf_page": p, "row_candidates": candidate_rows_by_page[p], "rows_without_oil_candidate": rows_without_oil_by_page[p]} for p in partial_table_pages],
            "verified_fatty_acid_component_record_count": sum(len(s.get("fatty_acid_components", [])) for s in samples),
            "records_with_aligned_fatty_acid_raw_candidates": sum(bool(s.get("fatty_acid_value_candidates")) for s in samples),
            "fatty_acid_value_raw_candidates_on_page_checked_rows": manual_fa_candidates,
            "other_measurement_raw_candidates_on_page_checked_rows": manual_other_candidates,
            "table_numeric_fragments_retained_in_raw_snippets": sum(len(s.get("unparsed_numeric_fragments", [])) for s in samples),
            "sample_rows_fully_verified": sum(s.get("verification_status") == "VERIFIED" for s in samples),
            "oil_verification_rule": "Only cells visually checked against their exact 300 DPI PDF crop are exposed as oil_content_pct. No OCR number from other columns is treated as an oil value.",
            "fatty_acid_policy": "No fatty-acid percentages are structured as measured values until each row/column cell is visually verified. Original OCR row fragments, headers and coordinates remain available for review.",
            "unit_policy": {"含油率（%）": "% as printed", "fatty_acid_columns": "unit is not stated in the printed header; raw readings remain candidates"},
            "unit_notes": samples_doc.get("table_headers"),
        },
        "database": {
            "import_status": db_status,
            "source_plants_imported": db_audit.get("plant_count_for_source") if db_audit else None,
            "ocr_row_candidates_imported": db_audit.get("ocr_row_candidate_count_for_source") if db_audit else None,
            "image_records_imported": db_audit.get("image_count_for_source") if db_audit else None,
            "verified_oil_records_in_database": db_audit.get("verified_oil_values_for_source") if db_audit else None,
            "verified_fatty_acid_records_in_database": db_audit.get("verified_fatty_acid_components_for_source") if db_audit else None,
            "sample_checks": db_audit.get("sample_checks", []) if db_audit else [],
            "attempt_detail": db_attempt,
            "importer": "scripts/import_pdf_data.sh",
            "sql_import_script": "database/import_pdf_course_data.sql",
            "sql_generator": "scripts/build_pdf_import_sql.py",
            "image_staging_script": "scripts/stage_pdf_crop_images.py",
            "idempotency_key": "stable source_key values on plants, OCR table-row candidates and image crops",
        },
    }
    write_json(ROOT / "data/data_quality_report.json", report)

    lines = [
        "# 课程资料 PDF 数据质量报告",
        "",
        f"报告由解析文件生成；来源文件 SHA-256：`{pdf_sha}`。",
        "",
        "## 解析概况",
        "",
        f"- 原始 PDF：{page_count} 页；每页是一张约 300 DPI 的扫描图，`pdftotext` 未提取到可用文字层。",
        f"- 已渲染并用于核验：{report['source_pdf']['rendered_review_images']} 页图像；OCR 覆盖 {len(ocr)} 页。PDF 第 1 页对应书内印刷第 11 页。",
        f"- 识别出植物条目：{len(plants)} 条；中文名、科属均有记录 {report['extraction']['plants_with_genus_zh_and_latin']} 条。拉丁名已从标题核对 {report['extraction']['scientific_names_verified_from_heading']} 条，另有 {report['extraction']['scientific_names_need_review']} 条需复核。",
        f"- 图片裁切：{len(image_crops)} 张，关联 {len(plants_with_images)}/{len(plants)} 个植物条目；每张保留 PDF 页码和裁切框。",
        f"- 数据库导入状态：`{db_status}`；数据库植物数：{db_audit.get('plant_count_for_source') if db_audit else '未能读取' }。",
        "",
        "## 含油率与化学表格",
        "",
        f"- 表格行候选：{len(samples)} 行，均关联到植物 ID；第 11 页因 OCR 行列塌缩，改为逐行对照页面图像转录 {len(manual_rows)} 行。含油率共 {len(oil_values)} 个单元格已逐格核对。",
        f"- 缺少可确认含油率单元格：{len(rows_without_oil)} 行；涉及 PDF 页：{', '.join(map(str, partial_table_pages)) or '无'}。",
        f"- 脂肪酸数据：{sum(bool(s.get('fatty_acid_value_candidates')) for s in samples)} 条采样行包含逐列对齐的原始候选（第 11 页共 {manual_fa_candidates} 个读数）；原表未标注单位，因此已确认并结构化为百分比的记录为 0。其他表格数字保留在 OCR 原始片段中，没有按列猜测或补零。",
        "- 脂肪酸列标题和含油率单位保留在 `data/oil_samples.json`；脂肪酸列的百分比单位在原表未逐列标注，作为待核验信息保留。",
        "",
        "## 缺失与待核验",
        "",
        f"- 描述字段缺失或无法识别：{sum(missing_by_field.values())} 个字段值；字段待核验：{sum(needs_review_by_field.values())} 个 OCR 字段值。描述 OCR 片段及坐标保存在每条植物记录中。",
        f"- 表格含油率候选缺失数量：{len(rows_without_oil)}；分布：" + (", ".join(f"PDF 第 {p} 页 {rows_without_oil_by_page[p]} 行" for p in partial_table_pages) if partial_table_pages else "无"),
        "- 整页完全无法识别：0 页。仍有含油率单元格缺失的页面：" + (", ".join(f"PDF 第 {p} 页" for p in partial_table_pages) or "无") + "。PDF 第 11 页其余可见表格行已转录；脂肪酸列的单位和数值仍需核验后再写入组分百分比字段。",
        "- 植物分类名称中需复核项、所有字段级缺失项、行候选及来源坐标详见 `data/data_quality_report.json`、`data/plants.json` 和 `data/oil_samples.json`。",
        "",
        "## 数据库抽样核验",
        "",
    ]
    if db_audit:
        lines.extend([f"- 导入器记录的植物数：{db_audit['plant_count_for_source']}；表格行候选：{db_audit['ocr_row_candidate_count_for_source']}；图片：{db_audit['image_count_for_source']}。",
                      f"- 抽样核验通过 {sum(all(x.get(k) for k in ('name_zh_matches','scientific_name_matches','family_zh_matches','genus_zh_matches','source_pages_matches')) for x in db_audit.get('sample_checks', []))}/{len(db_audit.get('sample_checks', []))} 条；详情见 `data/database_import_audit.json`。"])
    else:
        lines.append("- 尚无成功导入及数据库抽样结果。连接配置不可用时，数据库数量保持未读取，不记作 0。")
    lines.extend(["", "## 复现", "", "```sh", "scripts/render_source_pdf.sh", "swift scripts/ocr_scanned_pdf.swift tmp/pdfs/ocr-pages data/ocr/vision_ocr.json", "python3 scripts/build_pdf_data.py", "python3 scripts/crop_plant_images.py", "python3 scripts/build_data_quality_report.py", "scripts/import_pdf_data.sh", "```", ""])
    (ROOT / "docs/DATA_QUALITY.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote data/data_quality_report.json and docs/DATA_QUALITY.md; plants={len(plants)} crops={len(image_crops)} row_candidates={len(samples)} verified_oil={len(oil_values)}")


if __name__ == "__main__":
    main()
