import json
import unittest
from pathlib import Path

from scripts.build_trait_evidence_candidates import extract_candidates


ROOT = Path(__file__).resolve().parents[2]


class TraitEvidenceCandidatesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.plants = json.loads((ROOT / "data/plants.json").read_text(encoding="utf-8"))["plants"]
        cls.candidates = extract_candidates(cls.plants)

    def test_lanceolate_is_not_misclassified_as_needle(self):
        cycad = next(plant for plant in self.plants if plant["name_zh"] == "苏铁")
        cycad_rows = [row for row in self.candidates if row["plant_id"] == cycad["plant_id"]]
        self.assertTrue(any(row["trait_code"] == "leaf_shape" and row["option_code"] == "lanceolate" for row in cycad_rows))
        self.assertFalse(any(row["trait_code"] == "leaf_shape" and row["option_code"] == "needle" for row in cycad_rows))

    def test_explicit_pine_needle_description_remains_a_candidate(self):
        pine = next(plant for plant in self.plants if plant["name_zh"] == "云南松")
        pine_rows = [row for row in self.candidates if row["plant_id"] == pine["plant_id"]]
        self.assertTrue(any(row["trait_code"] == "leaf_shape" and row["option_code"] == "needle" for row in pine_rows))

    def test_candidates_keep_source_and_unreviewed_state(self):
        self.assertTrue(self.candidates)
        for row in self.candidates:
            self.assertEqual("EXTRACTED", row["verification_status"])
            self.assertEqual("course-pdf-2026", row["source_key"])
            self.assertGreaterEqual(row["source_pdf_page"], 1)
            self.assertTrue(row["source_excerpt"])


if __name__ == "__main__":
    unittest.main()
