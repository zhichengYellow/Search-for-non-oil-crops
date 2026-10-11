#!/usr/bin/env python3
"""Extract conservative, source-linked trait candidates from OCR snippets.

All rows are EXTRACTED candidates; this script never confirms a trait.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from pathlib import Path
from typing import Any

import fitz


RULES = [
    ("plant_form", "tree", r"乔木", "HIGH", None, "Explicit growth-form term."),
    ("plant_form", "shrub", r"灌木", "HIGH", None, "Explicit growth-form term."),
    ("plant_form", "herb", r"草本", "HIGH", None, "Explicit growth-form term."),
    ("plant_form", "vine", r"藤本", "HIGH", None, "Explicit growth-form term."),
    ("leaf_persistence", "evergreen", r"常绿", "HIGH", None, "Explicit persistence term; still requires source-page review."),
    ("leaf_persistence", "deciduous", r"落叶(?:乔木|灌木|树|植物)", "HIGH", None, "Explicit persistence term; avoids matching a species name alone."),
    ("cone_shape", "cone", r"球果", "HIGH", None, "Explicit botanical cone term; does not imply Pinus or any genus."),
]

LEAF_SHAPES = [
    # Do not treat 披针形 as 针形. That substring collision would classify
    # lanceolate leaves (and unrelated scale/bract descriptions) as needles.
    ("needle", r"(?<!披)针形|针状|针叶"),
    ("fan_shaped", r"扇形"),
    ("linear", r"线形|条形"),
    ("lanceolate", r"披针形"),
    ("ovate", r"卵形"),
    ("elliptic", r"椭圆形"),
    ("scale_like", r"鳞形"),
    ("pinnate_compound", r"羽状复叶"),
    ("palmate_lobed", r"掌状裂"),
    ("heart_shaped", r"心形"),
]

LEAF_COLORS = [
    ("blue_green", r"蓝绿色|蓝灰色"),
    ("yellow", r"黄色|黄绿色"),
    ("reddish", r"红色|红褐色"),
    ("silver", r"银灰色|银白色"),
    ("green", r"深绿色|浅绿色|绿色"),
]

LEAF_MARGINS = [
    ("serrated", r"锯齿(?:状|缘)?"),
    ("entire", r"全缘"),
    ("wavy", r"波状(?:缘)?"),
    ("lobed", r"羽状分裂|掌状分裂|分裂"),
]

LEAF_ARRANGEMENTS = [
    ("alternate", r"互生"),
    ("opposite", r"对生"),
    ("whorled", r"轮生"),
    ("basal", r"基生"),
]

BARK_TEXTURES = [
    ("fissured", r"纵裂|沟裂"),
    ("smooth", r"光滑"),
    ("peeling", r"片状剥落|树皮剥落"),
    ("fibrous", r"纤维状"),
]

CSV_FIELDS = [
    "candidate_key", "plant_id", "name_zh", "scientific_name_as_printed",
    "trait_code", "option_code", "value_text", "source_key",
    "source_pdf_page", "source_printed_page", "source_excerpt",
    "evidence_bbox_top_origin_json", "evidence_image_reference",
    "extraction_method", "mapping_confidence", "ambiguity_note",
    "verification_status",
]


def sha256(*parts: object) -> str:
    payload = "\x1f".join(map(str, parts)).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def top_bbox(bbox_bottom: list[float]) -> list[float]:
    x, y_bottom, width, height = map(float, bbox_bottom)
    return [x, max(0.0, 1.0 - y_bottom - height), width, height]


def close_to_leaf(text: str, match: re.Match[str], max_gap: int = 8) -> bool:
    prefix = text[max(0, match.start() - max_gap):match.start()]
    if "叶" in match.group():
        return True
    if "叶" not in prefix:
        suffix = text[match.end():match.end() + 3]
        return "叶" in suffix
    gap = prefix.rsplit("叶", 1)[1]
    return not re.search(r"[。；，,：:]", gap)


def candidate_from_snippet(
    plant: dict[str, Any], field_name: str, snippet: dict[str, Any], trait: str,
    option: str, matched_text: str, confidence: str, ambiguity: str | None,
) -> dict[str, Any] | None:
    pdf_page = int(snippet.get("pdf_page") or 0)
    bbox = snippet.get("bbox_normalized")
    if pdf_page < 1 or not bbox or len(bbox) != 4:
        return None
    bbox_top = top_bbox(bbox)
    excerpt = str(snippet.get("text", "")).strip()
    # Keep the source key stable across the PlantScope 3.0 ontology correction:
    # earlier candidates used fruit_shape/cone, while the corrected trait is
    # cone_shape/cone. The source identity remains anchored to the same text.
    key_trait = "fruit_shape" if trait == "cone_shape" and option == "cone" else trait
    key = sha256(plant["plant_id"], pdf_page, field_name, key_trait, option, excerpt, matched_text)
    image_ref = f"data/pdf_extract/text_evidence/{key[:20]}.jpg"
    return {
        "candidate_key": key,
        "plant_id": plant["plant_id"],
        "name_zh": plant.get("name_zh", ""),
        "scientific_name_as_printed": plant.get("scientific_name", ""),
        "trait_code": trait,
        "option_code": option,
        "value_text": "",
        "source_key": "course-pdf-2026",
        "source_pdf_page": pdf_page,
        "source_printed_page": pdf_page + 10,
        "source_excerpt": excerpt,
        "evidence_bbox_top_origin_json": json.dumps(bbox_top, separators=(",", ":")),
        "evidence_image_reference": image_ref,
        "extraction_method": f"RULE_TEXT_V1:{field_name}",
        "mapping_confidence": confidence,
        "ambiguity_note": ambiguity or "OCR text match only; the source image must be reviewed before confirmation.",
        "verification_status": "EXTRACTED",
        "_bbox_top": bbox_top,
        "_pdf_page": pdf_page,
    }


def extract_candidates(plants: list[dict[str, Any]]) -> list[dict[str, Any]]:
    found: dict[str, dict[str, Any]] = {}

    def add(plant: dict[str, Any], field: str, snippet: dict[str, Any], trait: str, option: str,
            match_text: str, confidence: str = "HIGH", ambiguity: str | None = None) -> None:
        row = candidate_from_snippet(plant, field, snippet, trait, option, match_text, confidence, ambiguity)
        if row:
            found.setdefault(row["candidate_key"], row)

    for plant in plants:
        fields = plant.get("fields") or {}
        for field_name in ("morphology", "distribution"):
            field = fields.get(field_name) or {}
            for snippet in field.get("raw_snippets") or []:
                text = str(snippet.get("text", ""))
                if field_name == "morphology":
                    for trait, option, pattern, confidence, _, note in RULES:
                        for match in re.finditer(pattern, text):
                            add(plant, field_name, snippet, trait, option, match.group(), confidence, note)

                    for option, pattern in LEAF_SHAPES:
                        for match in re.finditer(pattern, text):
                            if close_to_leaf(text, match):
                                note = "Leaf context is adjacent in OCR text; review the crop to exclude OCR/layout ambiguity."
                                add(plant, field_name, snippet, "leaf_shape", option, match.group(), "MEDIUM", note)

                    for option, pattern in LEAF_COLORS:
                        for match in re.finditer(pattern, text):
                            if close_to_leaf(text, match):
                                note = "Color term is adjacent to a leaf token; verify which leaf surface/season the source describes."
                                add(plant, field_name, snippet, "leaf_color", option, match.group(), "MEDIUM", note)

                    for option, pattern in LEAF_MARGINS:
                        for match in re.finditer(pattern, text):
                            if close_to_leaf(text, match):
                                add(plant, field_name, snippet, "leaf_margin", option, match.group(), "MEDIUM",
                                    "Margin term is adjacent to a leaf token; review the original sentence and page image.")

                    for option, pattern in LEAF_ARRANGEMENTS:
                        for match in re.finditer(pattern, text):
                            if close_to_leaf(text, match):
                                add(plant, field_name, snippet, "leaf_arrangement", option, match.group(), "MEDIUM",
                                    "Arrangement term is adjacent to a leaf token; review the original sentence and page image.")

                    bark_context = re.search(r"树皮[^。；;]{0,48}", text)
                    if bark_context:
                        for option, pattern in BARK_TEXTURES:
                            match = re.search(pattern, bark_context.group())
                            if match:
                                add(plant, field_name, snippet, "bark_texture", option, match.group(), "MEDIUM",
                                    "Texture is extracted from a bark sentence; confirm life stage and OCR against the crop.")

                if field_name == "distribution":
                    # Only explicit habitat phrases; province names alone are not
                    # converted into habitat or broad geographic taxonomy.
                    if re.search(r"(?:生于|生长于)[^。；;]{0,30}(?:森林|林中|林地)", text):
                        add(plant, field_name, snippet, "growth_habitat", "forest", "森林/林中/林地", "MEDIUM",
                            "Explicit forest-habitat phrase; verify the OCR and the source's habitat wording.")

    return sorted(found.values(), key=lambda row: (row["source_pdf_page"], row["plant_id"], row["trait_code"], row["option_code"]))


def render_text_evidence(pdf_path: Path, candidates: list[dict[str, Any]], output_dir: Path, dpi: int = 300) -> None:
    doc = fitz.open(pdf_path)
    matrix = fitz.Matrix(dpi / 72, dpi / 72)
    for row in candidates:
        page = doc[row["_pdf_page"] - 1]
        x, y, width, height = row["_bbox_top"]
        pad_x, pad_y = 0.006, 0.006
        clip = fitz.Rect(
            max(0, x - pad_x) * page.rect.width,
            max(0, y - pad_y) * page.rect.height,
            min(1, x + width + pad_x) * page.rect.width,
            min(1, y + height + pad_y) * page.rect.height,
        )
        pixmap = page.get_pixmap(matrix=matrix, clip=clip, alpha=False, annots=False)
        path = output_dir / f"{row['candidate_key'][:20]}.jpg"
        path.parent.mkdir(parents=True, exist_ok=True)
        pixmap.save(str(path), jpg_quality=95)
    doc.close()


def export_candidates(repo_root: Path, output_root: Path | None = None, dpi: int = 300) -> int:
    data_dir = repo_root / "data"
    plants = json.loads((data_dir / "plants.json").read_text(encoding="utf-8"))["plants"]
    candidates = extract_candidates(plants)
    output_root = output_root or (data_dir / "pdf_extract")
    image_dir = output_root / "text_evidence"
    render_text_evidence(repo_root / "docs/课程设计资料.pdf", candidates, image_dir, dpi)
    csv_path = data_dir / "trait_candidates.csv"
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    with csv_path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=CSV_FIELDS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(candidates)
    json_path = output_root / "trait_candidates.json"
    json_path.write_text(json.dumps(candidates, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return len(candidates)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output-root", type=Path, default=None)
    parser.add_argument("--dpi", type=int, default=300)
    args = parser.parse_args()
    total = export_candidates(args.repo_root.resolve(), args.output_root.resolve() if args.output_root else None, args.dpi)
    print(json.dumps({"trait_candidates": total, "status": "EXTRACTED"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
