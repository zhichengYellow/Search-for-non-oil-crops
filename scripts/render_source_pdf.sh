#!/bin/sh
set -eu

PDF_PATH=${1:-docs/课程设计资料.pdf}
OUT_DIR=${2:-tmp/pdfs/ocr-pages}
if [ ! -f "$PDF_PATH" ]; then
  echo "PDF not found: $PDF_PATH" >&2
  exit 2
fi
command -v pdftoppm >/dev/null 2>&1 || { echo 'Install Poppler (pdftoppm) first.' >&2; exit 2; }
mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR"/page-*.jpg
pdftoppm -jpeg -r 300 -jpegopt quality=95 "$PDF_PATH" "$OUT_DIR/page"
echo "Rendered 300 DPI source pages to $OUT_DIR."
