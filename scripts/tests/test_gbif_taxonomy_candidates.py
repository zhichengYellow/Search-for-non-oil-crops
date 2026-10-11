import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.fetch_gbif_taxonomy_candidates import candidate_record, load_names, main


class FakeResponse:
    status = 200

    def __init__(self, payload):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return None

    def read(self, _size=-1):
        return json.dumps(self.payload).encode()


class GbifCandidatePipelineTests(unittest.TestCase):
    def test_csv_loader_requires_scientific_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "names.csv"
            path.write_text("name_zh\n银杏\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "scientific_name"):
                load_names(path)

    def test_csv_loader_reads_curated_taxon_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "names.csv"
            path.write_text("scientific_name,name_zh,seed_source_url\nGinkgo biloba,银杏,https://example.org/taxa\n", encoding="utf-8")
            self.assertEqual(load_names(path)[0]["scientific_name"], "Ginkgo biloba")
            self.assertEqual(load_names(path)[0]["seed_source_url"], "https://example.org/taxa")

    def test_candidate_is_always_review_pending_taxonomy_and_retains_license_warning(self):
        row = {"scientific_name": "Ginkgo biloba", "name_zh": "银杏"}
        payload = {"usage": {"key": 123, "canonicalName": "Ginkgo biloba", "authorship": "L.", "rank": "SPECIES", "family": "Ginkgoaceae", "genus": "Ginkgo"}, "diagnostics": {"matchType": "EXACT", "confidence": 100}}
        item = candidate_record(row, payload, "2026-10-10T00:00:00Z")
        self.assertEqual(item["resolution"], "MATCH_REVIEW_REQUIRED")
        self.assertEqual(item["source_namespace"], "GBIF")
        self.assertEqual(item["external_taxon_id"], "123")
        self.assertEqual(item["license_code"], "REVIEW_REQUIRED_PER_SOURCE_TERMS")
        self.assertEqual(item["name_zh"], "银杏")

    def test_higher_rank_does_not_claim_species_match(self):
        item = candidate_record({"scientific_name": "Pinus"}, {"usage": {"key": 2, "canonicalName": "Pinus", "rank": "GENUS"}}, "2026-10-10T00:00:00Z")
        self.assertEqual(item["resolution"], "NEW_TAXON_CANDIDATE")
        self.assertIsNone(item["family_name"])

    def test_resume_retries_only_failed_rows_and_preserves_batch(self):
        payload = {"usage": {"key": 123, "canonicalName": "Ginkgo biloba", "rank": "SPECIES"}}
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = root / "manifest.csv"
            output = root / "candidates.jsonl"
            errors = root / "errors.csv"
            manifest.write_text("scientific_name,name_zh\nCycas revoluta,苏铁\nGinkgo biloba,银杏\n", encoding="utf-8")
            with patch("scripts.fetch_gbif_taxonomy_candidates.fetch_match", side_effect=[RuntimeError("temporary API error"), payload]):
                self.assertEqual(main(["--input", str(manifest), "--output", str(output), "--errors", str(errors), "--min-interval-seconds", "0.5"]), 0)
            state = json.loads(Path(str(output) + ".state.json").read_text(encoding="utf-8"))
            batch_uuid = state["batch_uuid"]
            first = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
            self.assertEqual([row["source_line"] for row in first], [3])
            self.assertEqual(len(state["errors"]), 1)

            with patch("scripts.fetch_gbif_taxonomy_candidates.fetch_match", return_value=payload) as fetch:
                self.assertEqual(main(["--input", str(manifest), "--output", str(output), "--errors", str(errors), "--min-interval-seconds", "0.5", "--resume"]), 0)
                fetch.assert_called_once_with("Cycas revoluta")
            completed = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
            self.assertEqual([row["source_line"] for row in completed], [2, 3])
            self.assertTrue(all(row["batch_uuid"] == batch_uuid for row in completed))
            self.assertEqual(json.loads(Path(str(output) + ".state.json").read_text(encoding="utf-8"))["errors"], {})
            self.assertEqual(errors.read_text(encoding="utf-8").strip(), "line,scientific_name,error")


if __name__ == "__main__":
    unittest.main()
