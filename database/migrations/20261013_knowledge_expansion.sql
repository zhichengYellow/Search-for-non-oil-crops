USE nonfood_oil_plants;

-- PlantScope 3.0 additive evidence migration. It deliberately keeps uncertain
-- chemistry outside fatty_acid_components and never changes course plant rows.
CREATE TABLE IF NOT EXISTS schema_migrations (
  version VARCHAR(32) PRIMARY KEY,
  script_name VARCHAR(160) NOT NULL,
  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

DELIMITER //
DROP PROCEDURE IF EXISTS ps3_add_column_if_missing//
CREATE PROCEDURE ps3_add_column_if_missing(
  IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT
)
BEGIN
  DECLARE column_count INT DEFAULT 0;
  SELECT COUNT(*) INTO column_count
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column;
  IF column_count = 0 THEN
    SET @ps3_ddl = CONCAT('ALTER TABLE `', REPLACE(p_table,'`','``'), '` ADD COLUMN `',
      REPLACE(p_column,'`','``'), '` ', p_definition);
    PREPARE ps3_stmt FROM @ps3_ddl;
    EXECUTE ps3_stmt;
    DEALLOCATE PREPARE ps3_stmt;
  END IF;
END//

DROP PROCEDURE IF EXISTS ps3_add_index_if_missing//
CREATE PROCEDURE ps3_add_index_if_missing(
  IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_definition TEXT
)
BEGIN
  DECLARE index_count INT DEFAULT 0;
  SELECT COUNT(*) INTO index_count
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index;
  IF index_count = 0 THEN
    SET @ps3_ddl = CONCAT('ALTER TABLE `', REPLACE(p_table,'`','``'), '` ADD ', p_definition);
    PREPARE ps3_stmt FROM @ps3_ddl;
    EXECUTE ps3_stmt;
    DEALLOCATE PREPARE ps3_stmt;
  END IF;
END//

DROP PROCEDURE IF EXISTS ps3_drop_check_if_exists//
CREATE PROCEDURE ps3_drop_check_if_exists(IN p_table VARCHAR(64), IN p_constraint VARCHAR(64))
BEGIN
  DECLARE constraint_count INT DEFAULT 0;
  SELECT COUNT(*) INTO constraint_count
  FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = p_table
    AND constraint_name = p_constraint AND constraint_type = 'CHECK';
  IF constraint_count > 0 THEN
    SET @ps3_ddl = CONCAT('ALTER TABLE `', REPLACE(p_table,'`','``'), '` DROP CHECK `',
      REPLACE(p_constraint,'`','``'), '`');
    PREPARE ps3_stmt FROM @ps3_ddl;
    EXECUTE ps3_stmt;
    DEALLOCATE PREPARE ps3_stmt;
  END IF;
END//

DROP PROCEDURE IF EXISTS ps3_add_fk_if_missing//
CREATE PROCEDURE ps3_add_fk_if_missing(IN p_table VARCHAR(64), IN p_constraint VARCHAR(64), IN p_definition TEXT)
BEGIN
  DECLARE fk_count INT DEFAULT 0;
  SELECT COUNT(*) INTO fk_count
  FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = p_table
    AND constraint_name = p_constraint AND constraint_type = 'FOREIGN KEY';
  IF fk_count = 0 THEN
    SET @ps3_ddl = CONCAT('ALTER TABLE `', REPLACE(p_table,'`','``'), '` ADD CONSTRAINT `',
      REPLACE(p_constraint,'`','``'), '` ', p_definition);
    PREPARE ps3_stmt FROM @ps3_ddl;
    EXECUTE ps3_stmt;
    DEALLOCATE PREPARE ps3_stmt;
  END IF;
END//
DELIMITER ;

CALL ps3_add_column_if_missing('plant_trait_values','source_key','VARCHAR(128) NULL');
CALL ps3_add_column_if_missing('plant_trait_values','extraction_method','VARCHAR(80) NULL');
CALL ps3_add_column_if_missing('plant_trait_values','mapping_confidence','ENUM(\'HIGH\',\'MEDIUM\',\'LOW\') NULL');
CALL ps3_add_column_if_missing('plant_trait_values','ambiguity_note','TEXT NULL');
CALL ps3_add_column_if_missing('plant_trait_values','evidence_image_reference','VARCHAR(500) NULL');
CALL ps3_add_index_if_missing('plant_trait_values','uq_trait_value_source_key','UNIQUE KEY `uq_trait_value_source_key` (`source_key`)');
ALTER TABLE plant_trait_values MODIFY COLUMN verification_status
  ENUM('EXTRACTED','REVIEW_PENDING','CONFIRMED','NEEDS_REVIEW','UNKNOWN','DISPUTED','REJECTED')
  NOT NULL DEFAULT 'NEEDS_REVIEW';
CALL ps3_add_index_if_missing('plant_trait_values','idx_trait_review_queue','KEY `idx_trait_review_queue` (`verification_status`,`updated_at`)');

CREATE TABLE IF NOT EXISTS fatty_acid_candidates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  candidate_key CHAR(64) NOT NULL UNIQUE,
  sample_id BIGINT NOT NULL,
  row_no SMALLINT UNSIGNED NULL,
  component_code VARCHAR(40) NULL,
  raw_value_text VARCHAR(120) NOT NULL,
  numeric_value_candidate DECIMAL(12,6) NULL,
  unit_candidate VARCHAR(24) NULL,
  reviewed_numeric_value DECIMAL(12,6) NULL,
  reviewed_unit VARCHAR(24) NULL,
  review_source_id BIGINT NULL,
  review_source_excerpt TEXT NULL,
  candidate_type VARCHAR(80) NOT NULL,
  collection_unit_candidate VARCHAR(160) NULL,
  testing_unit_candidate VARCHAR(160) NULL,
  sample_part_candidate VARCHAR(160) NULL,
  origin_candidate VARCHAR(160) NULL,
  source_id BIGINT NOT NULL,
  source_pdf_page SMALLINT UNSIGNED NOT NULL,
  source_printed_page SMALLINT UNSIGNED NULL,
  source_bbox JSON NULL,
  evidence_image_reference VARCHAR(500) NULL,
  source_record_reference VARCHAR(500) NULL,
  qc_flags TEXT NULL,
  verification_status ENUM('EXTRACTED','REVIEW_PENDING','CONFIRMED','REJECTED') NOT NULL DEFAULT 'REVIEW_PENDING',
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  review_reason VARCHAR(2000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_fa_candidate_sample FOREIGN KEY(sample_id) REFERENCES plant_oil_samples(id) ON DELETE CASCADE,
  CONSTRAINT fk_fa_candidate_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_fa_candidate_review_source FOREIGN KEY(review_source_id) REFERENCES plant_knowledge_sources(id) ON DELETE SET NULL,
  CONSTRAINT fk_fa_candidate_reviewer FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT chk_fa_candidate_number CHECK(numeric_value_candidate IS NULL OR numeric_value_candidate >= 0),
  CONSTRAINT chk_fa_candidate_confirmed CHECK(verification_status <> 'CONFIRMED' OR
    (component_code IS NOT NULL AND reviewed_numeric_value BETWEEN 0 AND 100 AND reviewed_unit = '%')),
  INDEX idx_fa_candidate_review(verification_status, source_pdf_page, id),
  INDEX idx_fa_candidate_sample(sample_id, verification_status)
) ENGINE=InnoDB;

CALL ps3_add_column_if_missing('fatty_acid_candidates','reviewed_numeric_value','DECIMAL(12,6) NULL');
CALL ps3_add_column_if_missing('fatty_acid_candidates','reviewed_unit','VARCHAR(24) NULL');
CALL ps3_add_column_if_missing('fatty_acid_candidates','review_source_id','BIGINT NULL');
CALL ps3_add_column_if_missing('fatty_acid_candidates','review_source_excerpt','TEXT NULL');
CALL ps3_add_fk_if_missing('fatty_acid_candidates','fk_fa_candidate_review_source',
  'FOREIGN KEY (`review_source_id`) REFERENCES `plant_knowledge_sources` (`id`) ON DELETE SET NULL');
CALL ps3_drop_check_if_exists('fatty_acid_candidates','chk_fa_candidate_confirmed');
ALTER TABLE fatty_acid_candidates ADD CONSTRAINT chk_fa_candidate_confirmed CHECK(
  verification_status <> 'CONFIRMED' OR
  (component_code IS NOT NULL AND reviewed_numeric_value BETWEEN 0 AND 100 AND reviewed_unit = '%')
);

CREATE TABLE IF NOT EXISTS knowledge_review_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entity_type VARCHAR(48) NOT NULL,
  entity_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  actor_user_id BIGINT NULL,
  source_id BIGINT NULL,
  reason VARCHAR(2000) NULL,
  before_data JSON NULL,
  after_data JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_knowledge_review_actor FOREIGN KEY(actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_knowledge_review_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE SET NULL,
  INDEX idx_knowledge_review_entity(entity_type, entity_id, created_at),
  INDEX idx_knowledge_review_actor(actor_user_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS external_taxon_candidates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  candidate_key CHAR(64) NOT NULL UNIQUE,
  plant_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  source_name VARCHAR(32) NOT NULL,
  query_name VARCHAR(220) NOT NULL,
  matched_name VARCHAR(220) NULL,
  accepted_name VARCHAR(220) NULL,
  taxon_key VARCHAR(80) NULL,
  taxon_rank VARCHAR(80) NULL,
  match_confidence VARCHAR(40) NULL,
  source_payload JSON NOT NULL,
  verification_status ENUM('EXTRACTED','REVIEW_PENDING','CONFIRMED','REJECTED') NOT NULL DEFAULT 'REVIEW_PENDING',
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  review_reason VARCHAR(2000) NULL,
  retrieved_at DATETIME NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_taxon_candidate_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  CONSTRAINT fk_taxon_candidate_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_taxon_candidate_reviewer FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_taxon_candidate_review(verification_status, source_name, id),
  INDEX idx_taxon_candidate_plant(plant_id, retrieved_at)
) ENGINE=InnoDB;

-- Include missing general-purpose options needed by rule-based descriptions.
INSERT IGNORE INTO trait_options(trait_id,code,label_zh,display_order)
SELECT t.id,'round','近圆形',85 FROM plant_traits t WHERE t.code='fruit_shape';
INSERT IGNORE INTO trait_options(trait_id,code,label_zh,display_order)
SELECT t.id,'rough','粗糙',25 FROM plant_traits t WHERE t.code='bark_texture';

-- Keep cones botanically distinct from fruits. Existing extracted course-PDF
-- candidates are moved to the dedicated trait with an append-only audit event;
-- confirmed facts are deliberately excluded from this ontology correction.
INSERT IGNORE INTO trait_categories(code,label_zh,display_order)
VALUES('cone','球果',55);
INSERT IGNORE INTO plant_traits(category_id,code,label_zh,search_weight,display_order)
SELECT c.id,'cone_shape','球果形态',0.85,10 FROM trait_categories c WHERE c.code='cone';
INSERT IGNORE INTO trait_options(trait_id,code,label_zh,display_order)
SELECT t.id,'cone','球果',10 FROM plant_traits t WHERE t.code='cone_shape';
UPDATE trait_categories SET label_zh='果实' WHERE code='fruit';
UPDATE plant_traits SET label_zh='果实形状' WHERE code='fruit_shape';
UPDATE trait_options SET is_active=0
WHERE trait_id=(SELECT id FROM plant_traits WHERE code='fruit_shape') AND code='cone';

INSERT INTO knowledge_review_events(entity_type,entity_id,action,source_id,reason,before_data,after_data)
SELECT 'plant_trait_value',v.id,'CONE_TRAIT_RECLASSIFIED',v.source_id,
  'Separate explicit cone evidence from botanical fruit-shape traits; candidate only.',
  JSON_OBJECT('trait_code','fruit_shape','option_code','cone','verification_status',v.verification_status),
  JSON_OBJECT('trait_code','cone_shape','option_code','cone','verification_status',v.verification_status)
FROM plant_trait_values v
JOIN plant_traits old_t ON old_t.id=v.trait_id AND old_t.code='fruit_shape'
JOIN trait_options old_o ON old_o.id=v.option_id AND old_o.trait_id=old_t.id AND old_o.code='cone'
JOIN plant_traits new_t ON new_t.code='cone_shape'
JOIN trait_options new_o ON new_o.trait_id=new_t.id AND new_o.code='cone'
WHERE v.source_key LIKE 'pdf-trait-v1-%'
  AND v.verification_status IN ('EXTRACTED','REVIEW_PENDING')
  AND NOT EXISTS (
    SELECT 1 FROM knowledge_review_events e
    WHERE e.entity_type='plant_trait_value' AND e.entity_id=v.id
      AND e.action='CONE_TRAIT_RECLASSIFIED'
  );

UPDATE plant_trait_values v
JOIN plant_traits old_t ON old_t.id=v.trait_id AND old_t.code='fruit_shape'
JOIN trait_options old_o ON old_o.id=v.option_id AND old_o.trait_id=old_t.id AND old_o.code='cone'
JOIN plant_traits new_t ON new_t.code='cone_shape'
JOIN trait_options new_o ON new_o.trait_id=new_t.id AND new_o.code='cone'
SET v.trait_id=new_t.id,v.option_id=new_o.id
WHERE v.source_key LIKE 'pdf-trait-v1-%'
  AND v.verification_status IN ('EXTRACTED','REVIEW_PENDING');

INSERT IGNORE INTO plant_traits(category_id,code,label_zh,search_weight,display_order)
SELECT c.id,'bark_color','树皮颜色',0.35,15 FROM trait_categories c WHERE c.code='bark';

INSERT IGNORE INTO trait_options(trait_id,code,label_zh,display_order)
SELECT t.id,v.code,v.label_zh,v.display_order
FROM plant_traits t JOIN (
  SELECT 'gray' code,'灰色或灰褐色' label_zh,10 display_order UNION ALL
  SELECT 'brown','褐色',20 UNION ALL
  SELECT 'red_brown','红褐色',30 UNION ALL
  SELECT 'dark','深色',40 UNION ALL
  SELECT 'other','其他颜色',50
) v WHERE t.code='bark_color';

INSERT IGNORE INTO schema_migrations(version,script_name)
VALUES('20261013','20261013_knowledge_expansion.sql');

DROP PROCEDURE IF EXISTS ps3_add_index_if_missing;
DROP PROCEDURE IF EXISTS ps3_add_column_if_missing;
DROP PROCEDURE IF EXISTS ps3_drop_check_if_exists;
DROP PROCEDURE IF EXISTS ps3_add_fk_if_missing;
