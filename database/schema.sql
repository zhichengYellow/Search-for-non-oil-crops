CREATE DATABASE IF NOT EXISTS nonfood_oil_plants CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE nonfood_oil_plants;

CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(60) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  email VARCHAR(160) NULL UNIQUE,
  role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_users_role_enabled(role, enabled)
) ENGINE=InnoDB;

CREATE TABLE plant_families (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_name_zh VARCHAR(100) NOT NULL,
  family_name_latin VARCHAR(160) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_family_zh(family_name_zh), UNIQUE KEY uq_family_latin(family_name_latin)
) ENGINE=InnoDB;
CREATE TABLE plant_genera (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id BIGINT NOT NULL,
  genus_name_zh VARCHAR(100) NULL,
  genus_name_latin VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_genus_family FOREIGN KEY(family_id) REFERENCES plant_families(id) ON DELETE RESTRICT,
  UNIQUE KEY uq_genus_latin(genus_name_latin), INDEX idx_genus_family(family_id)
) ENGINE=InnoDB;
CREATE TABLE plants (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_key VARCHAR(120) NULL UNIQUE,
  source_document VARCHAR(300) NULL,
  source_pdf_sha256 CHAR(64) NULL,
  source_printed_pages VARCHAR(80) NULL,
  scientific_name_status ENUM('UNVERIFIED','NEEDS_REVIEW','VERIFIED_FROM_PRINTED_HEADING') NOT NULL DEFAULT 'UNVERIFIED',
  source_ocr_json JSON NULL,
  genus_id BIGINT NULL,
  name_zh VARCHAR(160) NOT NULL,
  scientific_name VARCHAR(220) NULL,
  name_en VARCHAR(160) NULL,
  morphology TEXT NULL,
  distribution TEXT NULL,
  cultivation TEXT NULL,
  uses TEXT NULL,
  source_pages VARCHAR(80) NULL,
  verification_status ENUM('UNVERIFIED','NEEDS_REVIEW','VERIFIED') NOT NULL DEFAULT 'UNVERIFIED',
  is_public BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_plant_genus FOREIGN KEY(genus_id) REFERENCES plant_genera(id) ON DELETE SET NULL,
  UNIQUE KEY uq_plant_scientific_name(scientific_name),
  INDEX idx_plant_name(name_zh), INDEX idx_plant_scientific(scientific_name), INDEX idx_plant_public(is_public, name_zh), INDEX idx_plant_verification(verification_status)
) ENGINE=InnoDB;
CREATE TABLE plant_aliases (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plant_id BIGINT NOT NULL,
  alias VARCHAR(160) NOT NULL,
  language ENUM('zh','en','other') NOT NULL DEFAULT 'zh',
  source_status ENUM('UNVERIFIED','NEEDS_REVIEW','VERIFIED') NOT NULL DEFAULT 'UNVERIFIED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_alias_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  UNIQUE KEY uq_plant_alias(plant_id, alias, language), INDEX idx_alias_lookup(alias)
) ENGINE=InnoDB;
CREATE TABLE plant_images (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_key VARCHAR(160) NULL UNIQUE,
  plant_id BIGINT NOT NULL,
  stored_name VARCHAR(80) NOT NULL UNIQUE,
  alt_text VARCHAR(200) NULL,
  source_note VARCHAR(300) NULL,
  source_pdf_page SMALLINT UNSIGNED NULL,
  source_printed_page SMALLINT UNSIGNED NULL,
  source_crop_bbox JSON NULL,
  verification_status ENUM('UNVERIFIED','NEEDS_REVIEW','VERIFIED') NOT NULL DEFAULT 'UNVERIFIED',
  is_primary BOOLEAN NOT NULL DEFAULT FALSE,
  primary_plant_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_primary THEN plant_id ELSE NULL END) STORED,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- RESTRICT is required because primary_plant_id is a stored generated column
  -- derived from plant_id; InnoDB cannot cascade deletes into that base column.
  CONSTRAINT fk_image_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE RESTRICT,
  UNIQUE KEY uq_primary_image(primary_plant_id), INDEX idx_image_plant(plant_id, is_primary)
) ENGINE=InnoDB;
CREATE TABLE plant_oil_samples (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_key VARCHAR(160) NULL UNIQUE,
  record_kind ENUM('MANUAL_SAMPLE','OCR_ROW_CANDIDATE') NOT NULL DEFAULT 'MANUAL_SAMPLE',
  plant_id BIGINT NOT NULL,
  sample_label VARCHAR(120) NULL,
  oil_content_pct DECIMAL(7,3) NULL,
  oil_content_unit VARCHAR(24) NULL,
  oil_content_source_text VARCHAR(120) NULL,
  oil_content_status ENUM('MISSING_OR_UNREADABLE','NEEDS_REVIEW','VERIFIED') NOT NULL DEFAULT 'NEEDS_REVIEW',
  collection_unit_candidate VARCHAR(160) NULL,
  testing_unit_candidate VARCHAR(160) NULL,
  sample_part_candidate VARCHAR(160) NULL,
  origin_candidate VARCHAR(160) NULL,
  table_headers JSON NULL,
  source_snippets JSON NULL,
  other_measurements JSON NULL,
  source_pdf_page SMALLINT UNSIGNED NULL,
  source_printed_page SMALLINT UNSIGNED NULL,
  raw_source_text TEXT NULL,
  source_note VARCHAR(300) NULL,
  source_pages VARCHAR(80) NULL,
  verification_status ENUM('UNVERIFIED','NEEDS_REVIEW','VERIFIED') NOT NULL DEFAULT 'UNVERIFIED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_sample_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  CONSTRAINT chk_oil_pct CHECK(oil_content_pct IS NULL OR (oil_content_pct >= 0 AND oil_content_pct <= 100)),
  INDEX idx_sample_plant(plant_id), INDEX idx_sample_oil(oil_content_pct)
) ENGINE=InnoDB;
CREATE TABLE fatty_acid_components (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sample_id BIGINT NOT NULL,
  component_name VARCHAR(120) NOT NULL,
  percentage DECIMAL(7,3) NULL,
  source_pdf_page SMALLINT UNSIGNED NULL,
  source_unit VARCHAR(24) NULL,
  raw_value_text VARCHAR(120) NULL,
  source_bbox JSON NULL,
  source_note VARCHAR(300) NULL,
  verification_status ENUM('UNVERIFIED','NEEDS_REVIEW','VERIFIED') NOT NULL DEFAULT 'UNVERIFIED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_component_sample FOREIGN KEY(sample_id) REFERENCES plant_oil_samples(id) ON DELETE CASCADE,
  CONSTRAINT chk_component_pct CHECK(percentage IS NULL OR (percentage >= 0 AND percentage <= 100)),
  UNIQUE KEY uq_sample_component(sample_id, component_name), INDEX idx_component_name(component_name), INDEX idx_component_pct(percentage)
) ENGINE=InnoDB;
CREATE TABLE favorites (
  user_id BIGINT NOT NULL,
  plant_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY(user_id, plant_id),
  CONSTRAINT fk_favorite_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_favorite_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  INDEX idx_favorite_plant(plant_id)
) ENGINE=InnoDB;
CREATE TABLE audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(80) NOT NULL,
  entity_id BIGINT NULL,
  detail VARCHAR(1000) NULL,
  ip_address VARCHAR(45) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_actor FOREIGN KEY(actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_audit_time(created_at), INDEX idx_audit_entity(entity_type, entity_id)
) ENGINE=InnoDB;
