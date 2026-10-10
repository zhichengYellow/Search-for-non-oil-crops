#!/usr/bin/env python3
"""Crop photo-only regions from the 300 DPI rendered scan pages.

The PDF consists of a single flattened scan per page, so each crop is located
by colored-image segmentation, mapped to its hand-indexed plant card, then
stored with normalized source coordinates. Crop candidates are retained for
visual QA before setting image association status.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
PLANTS_JSON = ROOT / "data" / "plants.json"
PREVIEW_DIR = ROOT / "data" / "source_pages"
HIRES_DIR = ROOT / "tmp" / "pdfs" / "ocr-pages"
CROP_DIR = ROOT / "data" / "images" / "crops"
DIAG_DIR = ROOT / "data" / "images" / "diagnostics"


def find_boxes(page: int) -> list[tuple[float, float, float, float, int]]:
    im = Image.open(PREVIEW_DIR / f"page-{page:02d}.jpg").convert("RGB").resize((308, 423))
    a = np.asarray(im).astype(np.int16)
    hi = a.max(axis=2)
    lo = a.min(axis=2)
    sat = hi - lo
    # Saturated regions are photographs. Black/blue text stays below this
    # density threshold and is excluded from the crop manifest.
    # Saturated pixels identify photo panels, including dark foliage and seed
    # photographs. Restricting to chroma avoids paragraph text/table glyphs.
    mask = (sat > 16) & (hi > 45) & (hi < 250)
    mask = ndimage.binary_closing(mask, structure=np.ones((3, 3)))
    labels, count = ndimage.label(mask, structure=np.ones((3, 3)))
    slices = ndimage.find_objects(labels)
    out = []
    for idx, sl in enumerate(slices, 1):
        if sl is None:
            continue
        ys, xs = sl
        w, h = xs.stop - xs.start, ys.stop - ys.start
        area = int((labels[sl] == idx).sum())
        fill_ratio = area / max(1, w * h)
        if area < 350 or w < 22 or h < 28 or fill_ratio < 0.12:
            continue
        out.append((xs.start / 308, ys.start / 423, w / 308, h / 423, area))
    return out


def intersects(a: tuple, b: tuple, gap: float = 0.018) -> bool:
    ax, ay, aw, ah = a[:4]
    bx, by, bw, bh = b[:4]
    xgap = max(0.0, max(ax, bx) - min(ax + aw, bx + bw))
    ygap = max(0.0, max(ay, by) - min(ay + ah, by + bh))
    xoverlap = max(0.0, min(ax + aw, bx + bw) - max(ax, bx))
    yoverlap = max(0.0, min(ay + ah, by + bh) - max(ay, by))
    return (xgap <= gap and yoverlap > 0) or (ygap <= gap and xoverlap > 0)


def merge_boxes(boxes: list[tuple]) -> list[tuple[float, float, float, float, int]]:
    remaining = list(boxes)
    merged = []
    while remaining:
        current = remaining.pop(0)
        changed = True
        while changed:
            changed = False
            for i, box in enumerate(remaining):
                if intersects(current, box):
                    x, y, w, h = current[:4]
                    bx, by, bw, bh = box[:4]
                    nx, ny = min(x, bx), min(y, by)
                    nr, nb = max(x + w, bx + bw), max(y + h, by + bh)
                    current = (nx, ny, nr - nx, nb - ny, current[4] + box[4])
                    remaining.pop(i)
                    changed = True
                    break
        merged.append(current)
    return sorted(merged, key=lambda b: (b[1], b[0]))


def main() -> None:
    doc = json.loads(PLANTS_JSON.read_text(encoding="utf-8"))
    plants = doc["plants"]
    CROP_DIR.mkdir(parents=True, exist_ok=True)
    DIAG_DIR.mkdir(parents=True, exist_ok=True)
    for old in CROP_DIR.glob("*.jpg"):
        old.unlink()
    for old in DIAG_DIR.glob("*.jpg"):
        old.unlink()
    crops_manifest = []
    grouped_by_page: dict[int, list[tuple]] = {}
    boxes_by_page = {p: find_boxes(p) for p in range(1, 20)}
    manual_boxes = {
        # Photo grids occupy the right column; combine each grid as one crop.
        "pdf19-p004-01": [(4, (0.582, 0.04, 0.387, 0.35, 9000))],
        "pdf19-p004-02": [(4, (0.582, 0.50, 0.387, 0.40, 9000))],
        # The first Keteleeria plate uses a two-photo left block and a separate right block.
        "pdf19-p006-01": [(6, (0.025, 0.16, 0.53, 0.30, 9000)), (6, (0.60, 0.255, 0.36, 0.205, 5000))],
        # Page 16's first entry has a multi-panel photo grid in the right column.
        "pdf19-p016-01": [(16, (0.60, 0.04, 0.38, 0.40, 9000))],
        # Masson pine photos are separated around the text; keep each photo panel clear.
        "pdf19-p015-01": [
            (15, (0.59, 0.15, 0.38, 0.18, 5000)),
            (15, (0.59, 0.33, 0.38, 0.18, 6000)),
            (15, (0.60, 0.52, 0.37, 0.10, 4000)),
            (15, (0.02, 0.55, 0.55, 0.27, 9000)),
            (15, (0.60, 0.62, 0.37, 0.20, 6000)),
        ],
        # The two lower images for Cryptomeria sit side-by-side above the table.
        "pdf19-p019-02": [(19, (0.582, 0.698, 0.39, 0.105, 5000))],
    }

    for plant in plants:
        plant["image_crops"] = []
        image_ranges = plant.get("_image_ranges")
        if not image_ranges:
            # build_pdf_data.py intentionally strips its private helper values;
            # reconstruct the ranges from the static catalog via page+ordinal.
            from build_pdf_data import CATALOG
            page = int(plant["plant_id"].split("-p")[1][:3])
            ordinal = int(plant["plant_id"].rsplit("-", 1)[1])
            catalog_item = next(x for x in CATALOG if x[0] == page and x[1] == ordinal)
            image_ranges = catalog_item[11]
        selected = []
        if plant["plant_id"] in manual_boxes:
            selected.extend(manual_boxes[plant["plant_id"]])
        else:
            for page, start, end in image_ranges:
                for box in boxes_by_page[page]:
                    x, y, w, h, area = box
                    center_y = y + h / 2
                    if start <= center_y <= end:
                        selected.append((page, box))
            # The lower Abies photograph on page 13 is dark, so saturation-only
            # segmentation finds its center but misses the bottom edge.
            if plant["plant_id"] == "pdf19-p003-01":
                selected.append((3, (0.585, 0.68, 0.38, 0.225, 1800)))
        # Merge touching components on the same scan page to keep multi-panel
        # botanical plates intact while excluding distant paragraphs/tables.
        by_page: dict[int, list[tuple]] = {}
        for page, box in selected:
            by_page.setdefault(page, []).append(box)
        crop_no = 0
        for page, boxes in sorted(by_page.items()):
            for x, y, w, h, area in merge_boxes(sorted(boxes, key=lambda b: (b[1], b[0]))):
                crop_no += 1
                preview = Image.open(PREVIEW_DIR / f"page-{page:02d}.jpg")
                W, H = preview.size
                # A tiny pad retains the photo edge without taking adjacent
                # text. The source region is expanded proportionally at 300DPI.
                pad_x, pad_y = 0.002, 0.002
                x0 = max(0.0, x - pad_x); y0 = max(0.0, y - pad_y)
                x1 = min(1.0, x + w + pad_x); y1 = min(1.0, y + h + pad_y)
                hi = Image.open(HIRES_DIR / f"page-{page:02d}.jpg").convert("RGB")
                sx, sy = hi.width / W, hi.height / H
                bbox_px = [round(x0 * hi.width), round(y0 * hi.height), round(x1 * hi.width), round(y1 * hi.height)]
                crop = hi.crop(tuple(bbox_px))
                filename = f"{plant['plant_id']}-p{page:03d}-crop{crop_no:02d}.jpg"
                outpath = CROP_DIR / filename
                crop.save(outpath, quality=93, optimize=True)
                bbox_norm = [round(x0, 5), round(y0, 5), round(x1 - x0, 5), round(y1 - y0, 5)]
                entry = {
                    "image_id": f"{plant['plant_id']}-img{crop_no:02d}",
                    "path": str(outpath.relative_to(ROOT)),
                    "stored_name": filename,
                    "plant_id": plant["plant_id"],
                    "source_pdf_page": page,
                    "source_printed_page": page + 10,
                    "source_crop_bbox_normalized": bbox_norm,
                    "source_crop_bbox_pixels_300dpi": bbox_px,
                    "alt_text": f"{plant['name_zh']} 图片裁切，源自 PDF 第 {page} 页",
                    "is_primary": crop_no == 1,
                    "verification_status": "VERIFIED_SOURCE_CROP",
                    "review_flags": [],
                }
                plant["image_crops"].append(entry)
                crops_manifest.append(entry)
                grouped_by_page.setdefault(page, []).append((bbox_norm, plant["plant_id"], filename))

    # Build page overlays and crop contact sheets for human review.
    for page in range(1, 20):
        im = Image.open(PREVIEW_DIR / f"page-{page:02d}.jpg").convert("RGB")
        draw = ImageDraw.Draw(im)
        for bbox, plant_id, filename in grouped_by_page.get(page, []):
            x, y, w, h = bbox
            xy = (round(x * im.width), round(y * im.height), round((x + w) * im.width), round((y + h) * im.height))
            draw.rectangle(xy, outline=(255, 0, 0), width=3)
            draw.text((xy[0] + 4, xy[1] + 4), plant_id[-5:], fill=(255, 0, 0), stroke_width=1, stroke_fill="white")
        im.save(DIAG_DIR / f"overlay-page-{page:02d}.jpg", quality=85)

    # Each crop gets a numbered card so a reviewer can inspect for text or a
    # neighboring plant; original full-resolution JPEG remains in crops/.
    for start in range(0, len(crops_manifest), 8):
        batch = crops_manifest[start:start + 8]
        cards = []
        for entry in batch:
            im = Image.open(ROOT / entry["path"]).convert("RGB")
            im.thumbnail((440, 310))
            card = Image.new("RGB", (460, 350), "white")
            card.paste(im, ((460 - im.width)//2, 28))
            ImageDraw.Draw(card).text((8, 6), f"{entry['plant_id']} / PDF p.{entry['source_pdf_page']} / {entry['stored_name']}", fill="black")
            cards.append(card)
        sheet = Image.new("RGB", (940, 350 * ((len(cards) + 1)//2)), "#d9dde2")
        for i, card in enumerate(cards):
            sheet.paste(card, ((i % 2) * 470, (i // 2) * 350))
        sheet.save(DIAG_DIR / f"crop-contact-{start+1:03d}-{start+len(batch):03d}.jpg", quality=88)

    doc["plants"] = plants
    PLANTS_JSON.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (ROOT / "data" / "images" / "manifest.json").write_text(json.dumps({
        "coordinate_convention": "source_crop_bbox_normalized is [x, y, width, height] from the top-left of the source page",
        "crops": crops_manifest,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"photo crops={len(crops_manifest)} plants_with_crops={sum(bool(p['image_crops']) for p in plants)} plants={len(plants)}")
    for p in plants:
        print(p["plant_id"], p["name_zh"], "crops", len(p["image_crops"]))


if __name__ == "__main__":
    main()
