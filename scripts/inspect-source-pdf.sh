#!/bin/sh
# Read-only helper: inspect and render the original scan without modifying it.
set -eu
PDF_PATH=${1:-docs/课程设计资料.pdf}
if [ ! -f "$PDF_PATH" ]; then
  echo "PDF not found: $PDF_PATH" >&2
  echo "Place the original scan at docs/课程设计资料.pdf, then rerun." >&2
  exit 2
fi
command -v pdfinfo >/dev/null 2>&1 || { echo 'Install Poppler (pdfinfo/pdftoppm) first.' >&2; exit 2; }
REVIEW_DIR=${2:-docs/pdf-review}
mkdir -p "$REVIEW_DIR"
pdfinfo "$PDF_PATH" | tee "$REVIEW_DIR/pdfinfo.txt"
pdftoppm -jpeg -r 120 "$PDF_PATH" "$REVIEW_DIR/page"
echo "Rendered review images to $REVIEW_DIR. Original PDF was read only."
if command -v tesseract >/dev/null 2>&1; then
  echo 'Tesseract is available for assisted OCR; review extracted names and numbers against the page images.'
else
  echo 'Tesseract is not installed. This project uses scripts/ocr_scanned_pdf.swift (Apple Vision) for OCR; inspect the rendered page images before accepting extracted data.'
fi
