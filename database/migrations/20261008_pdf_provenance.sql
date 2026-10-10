USE nonfood_oil_plants;

-- One-time migration for databases created from the previous schema.sql.
ALTER TABLE plants
  ADD COLUMN source_key VARCHAR(120) NULL,
  ADD COLUMN source_document VARCHAR(300) NULL,
  ADD COLUMN source_pdf_sha256 CHAR(64) NULL,
  ADD COLUMN source_printed_pages VARCHAR(80) NULL,
  ADD COLUMN scientific_name_status ENUM('UNVERIFIED','NEEDS_REVIEW','VERIFIED_FROM_PRINTED_HEADING') NOT NULL DEFAULT 'UNVERIFIED',
  ADD COLUMN source_ocr_json JSON NULL,
  ADD UNIQUE KEY uq_plants_source_key(source_key);

ALTER TABLE plant_aliases
  ADD COLUMN source_status ENUM('UNVERIFIED','NEEDS_REVIEW','VERIFIED') NOT NULL DEFAULT 'UNVERIFIED';

ALTER TABLE plant_images
  ADD COLUMN source_key VARCHAR(160) NULL,
  ADD COLUMN source_pdf_page SMALLINT UNSIGNED NULL,
  ADD COLUMN source_printed_page SMALLINT UNSIGNED NULL,
  ADD COLUMN source_crop_bbox JSON NULL,
  ADD COLUMN verification_status ENUM('UNVERIFIED','NEEDS_REVIEW','VERIFIED') NOT NULL DEFAULT 'UNVERIFIED',
  ADD UNIQUE KEY uq_plant_images_source_key(source_key);

ALTER TABLE plant_oil_samples
  ADD COLUMN source_key VARCHAR(160) NULL,
  ADD COLUMN record_kind ENUM('MANUAL_SAMPLE','OCR_ROW_CANDIDATE') NOT NULL DEFAULT 'MANUAL_SAMPLE',
  ADD COLUMN oil_content_unit VARCHAR(24) NULL,
  ADD COLUMN oil_content_source_text VARCHAR(120) NULL,
  ADD COLUMN oil_content_status ENUM('MISSING_OR_UNREADABLE','NEEDS_REVIEW','VERIFIED') NOT NULL DEFAULT 'NEEDS_REVIEW',
  ADD COLUMN collection_unit_candidate VARCHAR(160) NULL,
  ADD COLUMN testing_unit_candidate VARCHAR(160) NULL,
  ADD COLUMN sample_part_candidate VARCHAR(160) NULL,
  ADD COLUMN origin_candidate VARCHAR(160) NULL,
  ADD COLUMN table_headers JSON NULL,
  ADD COLUMN source_snippets JSON NULL,
  ADD COLUMN other_measurements JSON NULL,
  ADD COLUMN source_pdf_page SMALLINT UNSIGNED NULL,
  ADD COLUMN source_printed_page SMALLINT UNSIGNED NULL,
  ADD COLUMN raw_source_text TEXT NULL,
  ADD UNIQUE KEY uq_plant_oil_samples_source_key(source_key);

ALTER TABLE fatty_acid_components
  ADD COLUMN source_pdf_page SMALLINT UNSIGNED NULL,
  ADD COLUMN source_unit VARCHAR(24) NULL,
  ADD COLUMN raw_value_text VARCHAR(120) NULL,
  ADD COLUMN source_bbox JSON NULL;
