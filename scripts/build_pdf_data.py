#!/usr/bin/env python3
"""Build auditable plant/sample JSON from the checked PDF OCR run.

The botanical catalog below is a transcription of visible headings in the
source pages. Descriptive passages remain OCR excerpts and are deliberately
marked NEEDS_REVIEW. Oil-content cells have a separate image-review ledger;
other table numerics remain unassigned OCR fragments until checked.
"""
from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PDF = ROOT / "docs" / "课程设计资料.pdf"
OCR_FILE = ROOT / "data" / "ocr" / "vision_ocr.json"
PLANTS_OUT = ROOT / "data" / "plants.json"
SAMPLES_OUT = ROOT / "data" / "oil_samples.json"
OIL_REVIEW_FILE = ROOT / "data" / "oil_value_verification.json"
MANUAL_ROWS_FILE = ROOT / "data" / "manual_table_rows.json"

# page, ordinal, Chinese title, Latin title as printed, family zh/Latin,
# genus zh/Latin, Chinese aliases, taxonomic citation/synonym note, OCR ranges,
# photo-card ranges. Coordinates are top-origin normalized fractions.
CATALOG = [
    (1,1,"苏铁","Cycas revoluta Thunb.","苏铁科","Cycadaceae","苏铁属","Cycas",["铁树"],None,[(1,0.03,0.99)],[(1,0.10,0.86)]),
    (2,1,"银杏","Ginkgo biloba L.","银杏科","Ginkgoaceae","银杏属","Ginkgo",["白果"],None,[(2,0.03,0.88),(3,0.00,0.47)],[(2,0.08,0.98),(3,0.03,0.25)]),
    (3,1,"云南黄果冷杉","Abies ernestii var. salouenensis (Bordères et Gaussen) W. C. Cheng et L. K. Fu","松科","Pinaceae","冷杉属","Abies",[],None,[(3,0.47,0.98)],[(3,0.47,0.95)]),
    (4,1,"巴山冷杉","Abies fargesii Franch.","松科","Pinaceae","冷杉属","Abies",[],None,[(4,0.03,0.49)],[(4,0.03,0.44)]),
    (4,2,"杉松","Abies holophylla Maxim.","松科","Pinaceae","冷杉属","Abies",[],"Abies yoneyanae K. Sato (synonym printed in brackets)",[(4,0.49,0.98)],[(4,0.49,0.96)]),
    (5,1,"怒江冷杉","Abies nukiangensis C. Y. Cheng et L. K. Fu","松科","Pinaceae","冷杉属","Abies",[],"Abies delavayi var. nukiangensis (W. C. Cheng et L. K. Fu) Farjon et Silba",[(5,0.03,0.47)],[(5,0.03,0.43)]),
    (5,2,"雪松","Cedrus deodara (Roxb.) G. Don","松科","Pinaceae","雪松属","Cedrus",[],None,[(5,0.47,0.98)],[(5,0.48,0.97)]),
    (6,1,"铁坚油杉","Keteleeria davidiana (Bertr.) Beissn.","松科","Pinaceae","油杉属","Keteleeria",[],None,[(6,0.03,0.56)],[(6,0.18,0.48)]),
    (6,2,"云南油杉","Keteleeria evelyniana Mast.","松科","Pinaceae","油杉属","Keteleeria",[],None,[(6,0.56,0.99)],[(6,0.53,0.92)]),
    (7,1,"油杉","Keteleeria fortunei (A. Murray bis) Carrière","松科","Pinaceae","油杉属","Keteleeria",[],None,[(7,0.03,0.57)],[(7,0.20,0.47)]),
    (7,2,"落叶松","Larix gmelinii (Rupr.) Kuzen.","松科","Pinaceae","落叶松属","Larix",[],None,[(7,0.57,0.99)],[(7,0.60,0.91)]),
    (8,1,"黄花落叶松","Larix olgensis A. Henry","松科","Pinaceae","落叶松属","Larix",[],None,[(8,0.03,0.49)],[(8,0.03,0.42)]),
    (8,2,"云杉","Picea asperata Mast.","松科","Pinaceae","云杉属","Picea",["粗枝云杉"],None,[(8,0.49,0.98)],[(8,0.49,0.89)]),
    (9,1,"鱼鳞云杉","Picea jezoensis var. microsperma (Lindl.) W. C. Cheng et L. K. Fu","松科","Pinaceae","云杉属","Picea",[],None,[(9,0.03,0.50)],[(9,0.16,0.39)]),
    (9,2,"红皮云杉","Picea koraiensis Nakai","松科","Pinaceae","云杉属","Picea",[],None,[(9,0.50,0.98)],[(9,0.58,0.90)]),
    (10,1,"西伯利亚云杉","Picea obovata Ledeb.","松科","Pinaceae","云杉属","Picea",["新疆云杉"],None,[(10,0.03,0.50)],[(10,0.03,0.40)]),
    (10,2,"青杄","Picea wilsonii Mast.","松科","Pinaceae","云杉属","Picea",[],None,[(10,0.50,0.98)],[(10,0.48,0.90)]),
    (11,1,"华山松","Pinus armandii Franch.","松科","Pinaceae","松属","Pinus",[],None,[(11,0.03,0.99)],[(11,0.03,0.72)]),
    (12,1,"白皮松","Pinus bungeana Zucc. ex Endl.","松科","Pinaceae","松属","Pinus",[],None,[(12,0.03,0.62)],[(12,0.12,0.52)]),
    (12,2,"高山松","Pinus densata Mast.","松科","Pinaceae","松属","Pinus",[],None,[(12,0.62,0.98)],[(12,0.57,0.91)]),
    (13,1,"赤松","Pinus densiflora Sieb.","松科","Pinaceae","松属","Pinus",[],None,[(13,0.03,0.55)],[(13,0.03,0.44)]),
    (13,2,"湿地松","Pinus elliottii Engelm.","松科","Pinaceae","松属","Pinus",[],None,[(13,0.55,0.98)],[(13,0.69,0.91)]),
    (14,1,"红松","Pinus koraiensis Sieb. et Zucc.","松科","Pinaceae","松属","Pinus",[],None,[(14,0.03,0.55)],[(14,0.03,0.45)]),
    (14,2,"华南五针松","Pinus kwangtungensis Chun et Tsiang","松科","Pinaceae","松属","Pinus",["广东松"],None,[(14,0.55,0.98)],[(14,0.51,0.86)]),
    (15,1,"马尾松","Pinus massoniana D. Don","松科","Pinaceae","松属","Pinus",[],None,[(15,0.03,0.98)],[(15,0.14,0.86)]),
    (16,1,"台湾五针松","Pinus morrisonicola Hayata","松科","Pinaceae","松属","Pinus",["台湾松","黄山松"],"Pinus formosana Hayata; Pinus taiwanensis Hayata",[(16,0.03,0.59)],[(16,0.03,0.50)]),
    (16,2,"新疆五针松","Pinus sibirica Du Tour","松科","Pinaceae","松属","Pinus",["西伯利亚红松"],None,[(16,0.59,0.98)],[(16,0.55,0.92)]),
    (17,1,"樟子松","Pinus sylvestris var. mongolica Litv.","松科","Pinaceae","松属","Pinus",[],None,[(17,0.03,0.60)],[(17,0.03,0.46)]),
    (17,2,"油松","Pinus tabuliformis Carr.","松科","Pinaceae","松属","Pinus",[],"Pinus tabuliformis var. tabuliformis (printed bracket text; verify ending)",[(17,0.60,0.98)],[(17,0.66,0.90)]),
    (18,1,"黑松","Pinus thunbergii Parl.","松科","Pinaceae","松属","Pinus",[],None,[(18,0.03,0.51)],[(18,0.03,0.44)]),
    (18,2,"云南松","Pinus yunnanensis Franch.","松科","Pinaceae","松属","Pinus",[],None,[(18,0.51,0.98)],[(18,0.50,0.90)]),
    (19,1,"金钱松","Pseudolarix amabilis (J. Nelson) Rehd.","松科","Pinaceae","金钱松属","Pseudolarix",[],"P. kaempferi Gordon",[(19,0.03,0.515)],[(19,0.03,0.44)]),
    (19,2,"柳杉","Cryptomeria fortune Hooibr. ex Otto et Dietrich","杉科","Taxodiaceae","柳杉属","Cryptomeria",["长叶孔雀松"],None,[(19,0.515,0.99)],[(19,0.68,0.96)]),
]

TABLE_HEADERS = ["采集单位", "测试单位", "测试部位", "产地", "含油率（%）", "碘值", "酸值", "皂化值", "C12:0", "C14:0", "C16:0", "C16:1", "C18:0", "C18:1", "C18:2", "C18:3", "C20:0", "C20:1"]


def dump(path: Path, obj: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def get_top(line: dict) -> float:
    return 1.0 - float(line["y"])


def label_of(text: str) -> str | None:
    for field, label in (("morphology", "特征"), ("distribution", "分布"), ("cultivation", "栽培"), ("uses", "用途")):
        if label in text:
            return field
    return None


def snippet_fields(lines: list[dict], record: dict) -> tuple[dict, list[str], list[dict]]:
    result = {}
    missing = []
    source = []
    for page_no, start, end in record["ocr_ranges"]:
        page = lines[page_no - 1]
        in_range = [dict(l, _top=get_top(l)) for l in page["lines"] if start <= get_top(l) < end]
        headings = [x["_top"] for x in in_range if "含油率及化学组分数据" in x["text"] or "采集单位" in x["text"]]
        table_start = min(headings) if headings else end
        in_range = [x for x in in_range if x["_top"] < table_start]
        for l in in_range:
            source.append({"pdf_page": page_no, "text": l["text"], "confidence": round(float(l["confidence"]), 3),
                           "bbox_normalized": [round(l[k], 5) for k in ("x", "y", "width", "height")]})
        for col_name, is_left in (("left", True), ("right", False)):
            def in_col(line: dict) -> bool:
                return line["x"] < 0.57 if is_left else line["x"] >= 0.57
            col_lines = sorted((x for x in in_range if in_col(x)), key=lambda x: x["_top"])
            label_positions = [(i, label_of(x["text"])) for i, x in enumerate(col_lines) if label_of(x["text"])]
            for idx, field in label_positions:
                if field is None:
                    continue
                end_idx = next((j for j, f in label_positions if j > idx), len(col_lines))
                selected = col_lines[idx:end_idx]
                first = selected[0]
                label = {"morphology":"特征", "distribution":"分布", "cultivation":"栽培", "uses":"用途"}[field]
                fragments = []
                for j, item in enumerate(selected):
                    raw = item["text"]
                    if j == 0:
                        pos = raw.find(label)
                        raw = raw[pos + len(label):].strip(" ：:，、") if pos >= 0 else raw
                    if raw.strip():
                        fragments.append({"pdf_page": page_no, "text": raw.strip(), "confidence": round(float(item["confidence"]), 3),
                                         "bbox_normalized": [round(item[k], 5) for k in ("x", "y", "width", "height")]})
                if fragments:
                    result.setdefault(field, []).extend(fragments)
    fields = {}
    for key in ("morphology", "distribution", "cultivation", "uses"):
        snippets = result.get(key, [])
        if snippets:
            fields[key] = {"value": " ".join(x["text"] for x in snippets), "status": "NEEDS_REVIEW",
                           "raw_snippets": snippets}
        else:
            fields[key] = {"value": None, "status": "MISSING_OR_UNREADABLE", "raw_snippets": []}
            missing.append(key)
    return fields, missing, source


def parse_table_rows(ocr_pages: list[dict]) -> list[dict]:
    records = []
    review_doc = json.loads(OIL_REVIEW_FILE.read_text(encoding="utf-8")) if OIL_REVIEW_FILE.exists() else {"verified_values": []}
    oil_review = {x["sample_id"]: x for x in review_doc["verified_values"]}
    for idx, item in enumerate(CATALOG):
        page_no, ordinal = item[0], item[1]
        plant_id = f"pdf19-p{page_no:03d}-{ordinal:02d}"
        source_pages = {p for p, _, _ in item[10]}
        table_headers = []
        for pno in source_pages:
            page = ocr_pages[pno - 1]
            lines = [dict(l, _top=get_top(l)) for l in page["lines"]]
            # This catalog item owns only its manually bounded card region.
            ranges = [r for r in item[10] if r[0] == pno]
            if not ranges:
                continue
            _, card_start, card_end = ranges[0]
            title_lines = [x for x in lines if "含油率及化学组分数据" in x["text"] and card_start <= x["_top"] < card_end]
            header_lines_all = [x for x in lines if "采集单位" in x["text"] and card_start <= x["_top"] < card_end]
            # Some small tables have no OCR-detected section title. The printed column header is the table anchor.
            anchors = title_lines[:]
            for header in header_lines_all:
                if not any(abs(header["_top"] - a["_top"]) < 0.05 for a in anchors):
                    anchors.append(header)
            for h in sorted(anchors, key=lambda x: x["_top"]):
                top = h["_top"]
                next_anchors = [x["_top"] for x in anchors if top + 0.01 < x["_top"] < card_end]
                end = min(next_anchors) if next_anchors else card_end
                title_candidates = [x["_top"] for x in lines if top < x["_top"] < end and re.search(r"^[\u4e00-\u9fff]{1,14}(?:（[^）]+）)?$", x["text"].strip())]
                # The next entry title is separated from its previous table by a visible card boundary.
                page_plant_starts = [start for rr in CATALOG if rr[0] == pno for pp, start, _ in rr[10] if pp == pno and top < start < end]
                if page_plant_starts:
                    end = min(end, min(page_plant_starts))
                header_lines = [x for x in lines if abs(x["_top"] - top) < 0.05 and ("采集单位" in x["text"] or "含油率（%）" in x["text"] or re.search(r"C\d+:\d", x["text"]))]
                if not header_lines:
                    continue
                header_page = pno
                table_headers.append({"pdf_page": header_page, "printed_page": header_page + 10, "headers": TABLE_HEADERS,
                                      "raw_header_snippets": [x["text"] for x in header_lines]})
                if pno == 11 and plant_id == "pdf19-p011-01":
                    # This table's columns are visible but its OCR rows are collapsed;
                    # manual_page_rows() below supplies the page-checked rows.
                    continue
                body = [x for x in lines if top + 0.018 < x["_top"] < end and x["_top"] > max(y["_top"] for y in header_lines) + 0.004]
                groups: list[list[dict]] = []
                for line in sorted(body, key=lambda x: x["_top"]):
                    if not groups or line["_top"] - groups[-1][0]["_top"] > 0.0055:
                        groups.append([line])
                    else:
                        groups[-1].append(line)
                row_no = 0
                for group in groups:
                    group = sorted(group, key=lambda x: x["x"])
                    raw = " | ".join(x["text"].strip() for x in group if x["text"].strip())
                    if not raw or not re.search(r"\d", raw):
                        continue
                    if len(group) == 1 and group[0]["text"].strip() == str(pno + 10) and get_top(group[0]) > 0.97:
                        # A lone page folio below a table is not a sample row.
                        continue
                    if any(token in raw for token in ("采集单位", "含油率", "C12:", "皂化值", "碘值", "酸值", "含油率及化学")):
                        continue
                    # Header fragments on multi-column pages and page folios are not sample rows.
                    if not any(re.search(r"\d", x["text"]) and x["x"] < 0.5 for x in group) and not any(re.search(r"种仁|种子|果实", x["text"]) for x in group):
                        continue
                    row_no += 1
                    oil_header = next((x for x in header_lines if "含油率（%）" in x["text"]), None)
                    oil_raw = None
                    oil_cell = None
                    if oil_header:
                        oil_x = oil_header["x"]
                        oil_cells = [x for x in group if abs(x["x"] - oil_x) <= 0.025 and re.fullmatch(r"[\s*•·]*\d+(?:\.\d+)?[\s*]*", x["text"].strip())]
                        if oil_cells:
                            oil_cell = min(oil_cells, key=lambda x: x["x"])
                            oil_raw = oil_cell["text"].strip(" *•·")
                    origin = next((x["text"] for x in group if re.search(r"省|市|县|区|蒙古|北京|上海|新疆|云南|四川|广东|广西|陕西|湖北|湖南|辽宁|黑龙江|福建|江西|浙江|江苏|青海|重庆|河南|河北|山西|吉林|西藏|贵州|甘肃|内蒙古|宁夏", x["text"])), None)
                    part = next((x["text"] for x in group if re.search(r"种仁|种子|果实", x["text"])), None)
                    sample_code_candidates = [x["text"] for x in group if re.fullmatch(r"[A-Z]{2,6}", x["text"].strip())]
                    row_top = round(sum(x["_top"] for x in group) / len(group), 5)
                    fragments = [{"text": x["text"], "confidence": round(float(x["confidence"]), 3),
                                  "bbox_normalized": [round(x[k], 5) for k in ("x", "y", "width", "height")]}
                                 for x in group]
                    sample_id = f"{plant_id}-p{pno:03d}-r{row_no:02d}"
                    oil_review_entry = oil_review.get(sample_id)
                    oil_value = None
                    oil_verification = {"status": "MISSING_OR_UNREADABLE" if not oil_raw else "NEEDS_REVIEW"}
                    if oil_review_entry:
                        if oil_raw is None and oil_review_entry.get("manual_visual_read"):
                            oil_raw = str(oil_review_entry["value_pct"])
                            bx = oil_review_entry["source_bbox_normalized"]
                            oil_cell = {"x": bx[0], "y": bx[1], "width": bx[2], "height": bx[3]}
                            fragments.append({
                                "text": oil_raw, "confidence": 1.0, "column_name": "含油率（%）",
                                "bbox_normalized": bx, "bbox_normalized_top_origin": oil_review_entry.get("source_bbox_normalized_top_origin"),
                                "transcription_status": "VISUALLY_TRANSCRIBED_FROM_SOURCE_PAGE",
                            })
                            raw += " | " + oil_raw + " [manual visual check: 含油率（%）]"
                        if oil_raw != str(oil_review_entry["value_pct"]):
                            raise ValueError(f"manual oil review no longer matches OCR candidate for {sample_id}: {oil_raw!r}")
                        if oil_review_entry["source_pdf_page"] != pno:
                            raise ValueError(f"manual oil review source page mismatch for {sample_id}")
                        oil_value = float(oil_review_entry["value_pct"])
                        oil_verification = {
                            "status": "VERIFIED_FROM_SOURCE_IMAGE",
                            "method": oil_review_entry["verification_method"],
                            "source_pdf_page": pno,
                            "source_bbox_normalized": oil_review_entry["source_bbox_normalized"],
                            "raw_text": oil_raw,
                        }
                    records.append({
                        "sample_id": f"{plant_id}-p{pno:03d}-r{row_no:02d}",
                        "source_key": sample_id,
                        "record_kind": "OCR_ROW_CANDIDATE",
                        "record_status": "NEEDS_REVIEW",
                        "plant_id": plant_id,
                        "source_pdf_page": pno,
                        "source_printed_page": pno + 10,
                        "sample_label": None,
                        "collection_unit_candidate": sample_code_candidates[0] if sample_code_candidates else None,
                        "testing_unit_candidate": sample_code_candidates[1] if len(sample_code_candidates) > 1 else None,
                        "sample_part_candidate": part,
                        "origin_candidate": origin,
                        "oil_content_pct": oil_value,
                        "oil_content_verification": oil_verification,
                        "oil_content_candidate": {"raw_text": oil_raw, "unit": "%", "status": "VERIFIED_FROM_SOURCE_IMAGE" if oil_review_entry else "NEEDS_REVIEW",
                                                  "source_bbox_normalized": [round(oil_cell["x"], 5), round(oil_cell["y"], 5), round(oil_cell["width"], 5), round(oil_cell["height"], 5)]} if oil_raw and oil_cell else None,
                        "other_measurements": [],
                        "fatty_acid_components": [],
                        "unparsed_numeric_fragments": [x for x in fragments if re.search(r"\d", x["text"])],
                        "table_headers": TABLE_HEADERS,
                        "table_header_unit_notes": {"含油率（%）":"%", "脂肪酸列":"原表列名为脂肪酸碳链，表头未显式重复单位符号；百分比单位待核验"},
                        "source_row_top_normalized": row_top,
                        "raw_table_record": raw,
                        "raw_snippets": fragments,
                        "verification_status": "NEEDS_REVIEW",
                        "verified_fields": ["oil_content_pct"] if oil_verification["status"] == "VERIFIED_FROM_SOURCE_IMAGE" else [],
                        "fields_needing_review": ["sample_row_identity", "sample_context", "other_measurements", "fatty_acid_components"],
                        "review_flags": ["OCR row identity/context and non-oil numeric cells need review; this is retained as a row candidate."],
                    })
        # Keep the pages carrying the source table in plant provenance.
        item_dict = None
        # update a side map after records have been generated
        TABLES_BY_PLANT[plant_id] = table_headers
    return records


def manual_page_rows() -> list[dict]:
    manual_doc = json.loads(MANUAL_ROWS_FILE.read_text(encoding="utf-8"))
    actual_hash = hashlib.sha256(PDF.read_bytes()).hexdigest()
    if manual_doc.get("source_pdf_sha256") != actual_hash:
        raise ValueError("Manual table transcription is tied to a different PDF hash")
    review_doc = json.loads(OIL_REVIEW_FILE.read_text(encoding="utf-8"))
    reviews = {x["sample_id"]: x for x in review_doc.get("verified_values", [])}
    headers = TABLE_HEADERS
    fa_headers = headers[8:]
    centers = {
        "采集单位": 0.06, "测试单位": 0.13, "测试部位": 0.19, "产地": 0.27,
        "含油率（%）": 0.34, "碘值": 0.40, "酸值": 0.46, "皂化值": 0.52,
        "C12:0": 0.56, "C14:0": 0.60, "C16:0": 0.645, "C16:1": 0.69,
        "C18:0": 0.735, "C18:1": 0.78, "C18:2": 0.825, "C18:3": 0.87,
        "C20:0": 0.915, "C20:1": 0.96,
    }
    out = []
    for row in manual_doc["rows"]:
        row_no = int(row["row_no"])
        plant_id = "pdf19-p011-01"
        sample_id = f"{plant_id}-p011-r{row_no:02d}"
        row_center = round(0.771 + (row_no - 1) * 0.0168, 5)
        row_top = round(row_center - 0.0045, 5)
        row_height = 0.009
        row_bbox = [0.03, row_top, 0.95, row_height]
        def cell_bbox(header: str) -> list[float]:
            x = centers[header]
            return [round(x - 0.018, 5), round(1 - (row_top + row_height), 5), 0.036, row_height]
        raw_cells = {
            "采集单位": row.get("collection_unit"), "测试单位": row.get("testing_unit"),
            "测试部位": row.get("sample_part"), "产地": row.get("origin"),
            "含油率（%）": row.get("oil_content_pct"), **row["measurements"], **row["fatty_acids"],
        }
        fragments = []
        for header in headers:
            value = raw_cells.get(header)
            if value is None:
                continue
            fragments.append({
                "column_name": header, "text": str(value), "confidence": 1.0,
                "bbox_normalized": cell_bbox(header),
                "bbox_normalized_top_origin": [round(centers[header] - 0.018, 5), row_top, 0.036, row_height],
                "transcription_status": "VISUALLY_TRANSCRIBED_FROM_SOURCE_PAGE",
            })
        fa_candidates = [
            {"component_name": name, "raw_value_text": str(value), "percentage": None, "unit": None,
             "verification_status": "NEEDS_REVIEW_UNIT_NOT_STATED",
             "source_bbox_normalized": cell_bbox(name),
             "source_bbox_normalized_top_origin": [round(centers[name] - 0.018, 5), row_top, 0.036, row_height]}
            for name, value in row["fatty_acids"].items() if value is not None
        ]
        other_candidates = [
            {"measurement_name": name, "raw_value_text": str(value), "value": None, "unit": None,
             "verification_status": "NEEDS_REVIEW_UNIT_NOT_STATED",
             "source_bbox_normalized": cell_bbox(name),
             "source_bbox_normalized_top_origin": [round(centers[name] - 0.018, 5), row_top, 0.036, row_height]}
            for name, value in row["measurements"].items() if value is not None
        ]
        oil_text = row.get("oil_content_pct")
        review = reviews.get(sample_id)
        if oil_text is not None:
            if review is None or str(review["value_pct"]) != str(oil_text) or review["source_pdf_page"] != 11:
                raise ValueError(f"Missing/mismatched page-image oil verification for {sample_id}")
            oil_value = float(oil_text)
            oil_status = "VERIFIED_FROM_SOURCE_IMAGE"
            oil_bbox = cell_bbox("含油率（%）")
            oil_verification = {
                "status": oil_status, "method": review["verification_method"], "source_pdf_page": 11,
                "source_bbox_normalized": oil_bbox,
                "source_bbox_normalized_top_origin": [round(centers["含油率（%）"] - 0.018, 5), row_top, 0.036, row_height],
                "raw_text": oil_text,
            }
        else:
            oil_value = None
            oil_status = "MISSING_OR_UNREADABLE"
            oil_bbox = None
            oil_verification = {"status": oil_status, "source_pdf_page": 11}
        row_sequence = [raw_cells.get(x) for x in headers]
        out.append({
            "sample_id": sample_id, "source_key": sample_id, "record_kind": "OCR_ROW_CANDIDATE",
            "record_status": "NEEDS_REVIEW", "manual_row_transcription": True,
            "plant_id": plant_id, "source_pdf_page": 11, "source_printed_page": 21,
            "sample_label": None, "collection_unit_candidate": row.get("collection_unit"),
            "testing_unit_candidate": row.get("testing_unit"), "sample_part_candidate": row.get("sample_part"),
            "origin_candidate": row.get("origin"), "oil_content_pct": oil_value,
            "oil_content_verification": oil_verification,
            "oil_content_candidate": {"raw_text": oil_text, "unit": "%", "status": oil_status,
                                      "source_bbox_normalized": oil_bbox,
                                      "source_bbox_normalized_top_origin": [round(centers["含油率（%）"] - 0.018, 5), row_top, 0.036, row_height]} if oil_text is not None else None,
            "other_measurements": [], "other_measurement_candidates": other_candidates,
            "fatty_acid_components": [], "fatty_acid_value_candidates": fa_candidates,
            "unparsed_numeric_fragments": fragments, "raw_table_cells": raw_cells,
            "table_headers": headers, "table_header_records": TABLES_BY_PLANT.get(plant_id, []),
            "table_header_unit_notes": {"含油率（%）": "%", "脂肪酸列": "原表未标注单位；原始读数保留为待核验候选，不写入百分比字段"},
            "source_row_top_normalized": row_center, "source_row_bbox_normalized_top_origin": row_bbox,
            "raw_table_record": " | ".join("" if x is None else str(x) for x in row_sequence),
            "raw_snippets": fragments, "verification_status": "NEEDS_REVIEW",
            "verified_fields": ["oil_content_pct"] if oil_text is not None else [],
            "fields_needing_review": ["sample_context", "other_measurement_units", "fatty_acid_units_and_values"],
            "review_flags": ["Page-checked row transcription; fatty-acid/other measurement units are not stated in the table header, so values remain raw candidates."],
        })
    return out


TABLES_BY_PLANT: dict[str, list[dict]] = {}


def main() -> None:
    if not PDF.exists():
        raise SystemExit(f"missing source PDF: {PDF}")
    if not OCR_FILE.exists():
        raise SystemExit(f"missing OCR result: {OCR_FILE}; run scripts/ocr_scanned_pdf.swift first")
    pages = json.loads(OCR_FILE.read_text(encoding="utf-8"))
    if len(pages) != 19:
        raise SystemExit(f"expected OCR for 19 pages, found {len(pages)}")
    sha256 = hashlib.sha256(PDF.read_bytes()).hexdigest()
    plants = []
    for item in CATALOG:
        (page_no, ordinal, name_zh, scientific_name, family_zh, family_latin,
         genus_zh, genus_latin, aliases, synonym_note, ocr_ranges, image_ranges) = item
        plant_id = f"pdf19-p{page_no:03d}-{ordinal:02d}"
        fields, missing, source_lines = snippet_fields(pages, {"ocr_ranges": ocr_ranges})
        if synonym_note:
            review = ["scientific_synonym_or_author_citation_needs_visual_confirmation"]
        else:
            review = []
        if not fields["morphology"]["value"]:
            review.append("morphology_excerpt_missing_or_unreadable")
        identified = ["name_zh", "family_zh", "family_latin", "genus_zh", "genus_latin"]
        latin_status = "VERIFIED_FROM_PRINTED_HEADING" if plant_id not in {"pdf19-p019-02", "pdf19-p017-02", "pdf19-p007-02"} else "NEEDS_REVIEW"
        identified.append("scientific_name")
        if aliases:
            identified.append("aliases")
        plants.append({
            "plant_id": plant_id,
            "source_key": plant_id,
            "data_source": "docs/课程设计资料.pdf",
            "source_pdf_pages": sorted({p for p, _, _ in ocr_ranges} | {p for p, _, _ in image_ranges}),
            "source_printed_pages": sorted({p + 10 for p in ({x for x, _, _ in ocr_ranges} | {x for x, _, _ in image_ranges})}),
            "source_pdf_sha256": sha256,
            "entry_ordinal_on_start_page": ordinal,
            "name_zh": name_zh,
            "scientific_name": scientific_name,
            "scientific_name_status": latin_status,
            "taxonomy_verification": {
                "name_zh": "VERIFIED_FROM_PRINTED_HEADING",
                "scientific_name": latin_status,
                "family_zh": "VERIFIED_FROM_PRINTED_HEADING",
                "family_latin": "VERIFIED_FROM_PRINTED_HEADING",
                "genus_zh": "VERIFIED_FROM_PRINTED_HEADING",
                "genus_latin": "VERIFIED_FROM_PRINTED_HEADING",
            },
            "scientific_synonyms_raw": synonym_note,
            "aliases": [{"value": alias, "source_status": "NEEDS_REVIEW"} for alias in aliases],
            "family_zh": family_zh,
            "family_latin": family_latin,
            "genus_zh": genus_zh,
            "genus_latin": genus_latin,
            "fields": fields,
            "raw_source_snippets": source_lines,
            "identified_fields": identified + [k for k, v in fields.items() if v["value"]],
            "missing_fields": missing,
            "review_flags": review + (["descriptive_fields_are_raw_OCR_excerpts_and_need_human_review"] if any(v["value"] for v in fields.values()) else []),
            "verification_status": "NEEDS_REVIEW" if (review or missing or any(v["status"] == "NEEDS_REVIEW" for v in fields.values())) else "VERIFIED",
            "image_crops": [],
            "_ocr_ranges": ocr_ranges,
            "_image_ranges": image_ranges,
        })
    samples = parse_table_rows(pages)
    # Vision OCR collapses the dense full-width table on PDF page 11. Replace
    # its incomplete row fragments with an explicit page-checked transcription.
    samples = [s for s in samples if not (s["source_pdf_page"] == 11 and s["plant_id"] == "pdf19-p011-01")]
    samples.extend(manual_page_rows())
    for sample in samples:
        sample.setdefault("table_header_records", TABLES_BY_PLANT.get(sample["plant_id"], []))
    samples.sort(key=lambda s: (s["source_pdf_page"], s.get("source_row_top_normalized", 0), s["sample_id"]))
    review_ids = {x["sample_id"] for x in json.loads(OIL_REVIEW_FILE.read_text(encoding="utf-8"))["verified_values"]}
    measured_ids = {s["sample_id"] for s in samples if s.get("oil_content_verification", {}).get("status") == "VERIFIED_FROM_SOURCE_IMAGE"}
    if review_ids != measured_ids:
        raise ValueError(f"oil review ledger/sample mismatch: unused={sorted(review_ids-measured_ids)}, missing={sorted(measured_ids-review_ids)}")
    # Data artifact keeps stable IDs and review metadata; internal page-range helpers are omitted.
    for plant in plants:
        plant.pop("_ocr_ranges", None)
        plant.pop("_image_ranges", None)
        plant["table_headers"] = TABLES_BY_PLANT.get(plant["plant_id"], [])
    dump(PLANTS_OUT, {"source_pdf": "docs/课程设计资料.pdf", "source_pdf_sha256": sha256,
                      "coordinate_convention": "OCR bbox_normalized uses x from left and y from bottom (Vision normalized coordinates); convert to top-origin with y_top=1-y-height. Image crop boxes use top-origin.",
                      "extraction_method": "macOS Vision OCR plus manual transcription of printed botanical headings; OCR excerpts retained with bounding boxes",
                      "pdf_page_count": len(pages), "plants": plants})
    dump(SAMPLES_OUT, {"source_pdf": "docs/课程设计资料.pdf", "source_pdf_sha256": sha256,
                       "coordinate_convention": "OCR bbox_normalized uses x from left and y from bottom (Vision normalized coordinates); convert to top-origin with y_top=1-y-height.",
                       "table_headers": TABLE_HEADERS, "oil_value_verification_file": "data/oil_value_verification.json",
                       "manual_table_transcriptions": ["data/manual_table_rows.json"],
                       "verified_numeric_policy": "Only oil cells explicitly matched to the printed cell in the image-review ledger are exposed as oil_content_pct. Unchecked oil candidates, other measurements and fatty-acid values remain raw fragments; absent values remain null.",
                       "samples": samples})
    print(f"plants={len(plants)} samples={len(samples)} source_sha256={sha256}")
    print("OCR field excerpts needing review=", sum(1 for p in plants for f in p["fields"].values() if f["value"]))


if __name__ == "__main__":
    main()
