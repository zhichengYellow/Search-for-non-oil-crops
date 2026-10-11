import json
import unittest
from pathlib import Path

from scripts.build_pdf_evidence_artifacts import (
    build_fatty_acid_candidate_rows,
    build_verified_rows,
    collect_review_boxes,
    inventory_pdf,
)


ROOT = Path(__file__).resolve().parents[2]


class PdfEvidenceArtifactTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.plants = json.loads((ROOT / "data/plants.json").read_text(encoding="utf-8"))
        cls.samples = json.loads((ROOT / "data/oil_samples.json").read_text(encoding="utf-8"))
        cls.manual = json.loads((ROOT / "data/manual_table_rows.json").read_text(encoding="utf-8"))
        cls.verified = json.loads((ROOT / "data/oil_value_verification.json").read_text(encoding="utf-8"))

    def test_scanned_pdf_inventory_preserves_physical_and_printed_pages(self):
        inventory = inventory_pdf(
            ROOT / "docs/课程设计资料.pdf",
            self.plants["plants"],
            ROOT / "data/source_pages",
        )
        self.assertEqual(19, inventory["page_count"])
        self.assertEqual(0, inventory["text_layer_summary"]["pages_with_text"])
        self.assertEqual(19, inventory["text_layer_summary"]["pages_without_text"])
        self.assertEqual(21, inventory["pages"][10]["source_printed_page"])

    def test_fatty_acid_rows_are_candidates_not_percentages(self):
        rows = build_fatty_acid_candidate_rows(self.plants, self.samples, self.manual)
        self.assertEqual(97, len(rows))
        self.assertEqual({"REVIEW_PENDING"}, {row["verification_status"] for row in rows})
        self.assertEqual({""}, {row["unit_candidate"] for row in rows})
        self.assertEqual({"pdf19-p011-01"}, {row["plant_id"] for row in rows})
        self.assertEqual({21}, {int(row["source_printed_page"]) for row in rows})
        anomaly_rows = {row["row_no"] for row in rows if row["qc_flags"]}
        self.assertEqual({"6", "7"}, anomaly_rows)

    def test_empty_verified_export_does_not_promote_candidates(self):
        self.assertEqual([], build_verified_rows(self.samples))

    def test_table_crop_groups_are_plant_scoped(self):
        boxes = collect_review_boxes(self.samples["samples"], self.verified["verified_values"])
        self.assertEqual(33, len(boxes))
        self.assertIn(("pdf19-p002-01", 3), boxes)
        self.assertIn(("pdf19-p003-01", 3), boxes)
        self.assertGreater(len(boxes[("pdf19-p011-01", 11)]), 11)


if __name__ == "__main__":
    unittest.main()
