USE nonfood_oil_plants;
SET NAMES utf8mb4;

-- PlantScope 3.2 additive knowledge/dataset/import model.
-- This migration does not alter or delete existing plant, sample, oil, image,
-- trait or user rows. Candidate rows are never public facts by default.
CREATE TABLE IF NOT EXISTS plant_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_key VARCHAR(80) NOT NULL,
  category_type ENUM('APPLICATION','GROWTH_FORM','DATASET_VIEW') NOT NULL,
  label_zh VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  display_order SMALLINT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uq_plant_category_key(category_key),
  INDEX idx_plant_category_type(category_type,is_active,display_order)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plant_category_relations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plant_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  source_excerpt TEXT NULL,
  verification_status ENUM('CONFIRMED','NEEDS_REVIEW','UNKNOWN','DISPUTED') NOT NULL DEFAULT 'NEEDS_REVIEW',
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_plant_category_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  CONSTRAINT fk_plant_category_category FOREIGN KEY(category_id) REFERENCES plant_categories(id) ON DELETE RESTRICT,
  CONSTRAINT fk_plant_category_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_plant_category_reviewer FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_plant_category_evidence(plant_id,category_id,source_id),
  INDEX idx_category_relation_public(category_id,verification_status,plant_id),
  INDEX idx_category_relation_plant(plant_id,verification_status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plant_datasets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dataset_code VARCHAR(64) NOT NULL,
  label_zh VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_plant_dataset_code(dataset_code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plant_dataset_relations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plant_id BIGINT NOT NULL,
  dataset_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  verification_status ENUM('CONFIRMED','NEEDS_REVIEW','UNKNOWN','DISPUTED') NOT NULL DEFAULT 'NEEDS_REVIEW',
  evidence_note VARCHAR(1000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_dataset_relation_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  CONSTRAINT fk_dataset_relation_dataset FOREIGN KEY(dataset_id) REFERENCES plant_datasets(id) ON DELETE RESTRICT,
  CONSTRAINT fk_dataset_relation_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  UNIQUE KEY uq_dataset_relation(plant_id,dataset_id,source_id),
  INDEX idx_dataset_relation_lookup(dataset_id,verification_status,plant_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plant_external_identifiers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plant_id BIGINT NOT NULL,
  source_namespace VARCHAR(100) NOT NULL,
  external_id VARCHAR(180) NOT NULL,
  source_id BIGINT NOT NULL,
  source_url VARCHAR(1000) NULL,
  license_code VARCHAR(120) NULL,
  attribution TEXT NULL,
  verification_status ENUM('CONFIRMED','NEEDS_REVIEW','UNKNOWN','DISPUTED') NOT NULL DEFAULT 'NEEDS_REVIEW',
  retrieved_at DATETIME NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_external_identifier_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  CONSTRAINT fk_external_identifier_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_external_identifier_reviewer FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_external_namespace_id(source_namespace,external_id),
  INDEX idx_external_identifier_plant(plant_id,verification_status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plant_taxonomy_references (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plant_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  original_scientific_name VARCHAR(220) NULL,
  accepted_scientific_name VARCHAR(220) NULL,
  authorship VARCHAR(220) NULL,
  taxonomic_rank VARCHAR(60) NULL,
  kingdom_name VARCHAR(120) NULL,
  phylum_name VARCHAR(160) NULL,
  class_name VARCHAR(160) NULL,
  order_name VARCHAR(160) NULL,
  family_name VARCHAR(160) NULL,
  genus_name VARCHAR(160) NULL,
  source_namespace VARCHAR(100) NULL,
  external_taxon_id VARCHAR(180) NULL,
  source_url VARCHAR(1000) NULL,
  license_code VARCHAR(120) NULL,
  source_payload JSON NULL,
  verification_status ENUM('CONFIRMED','NEEDS_REVIEW','UNKNOWN','DISPUTED') NOT NULL DEFAULT 'NEEDS_REVIEW',
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_taxonomy_reference_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  CONSTRAINT fk_taxonomy_reference_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_taxonomy_reference_reviewer FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_taxonomy_reference_external(plant_id,source_id,source_namespace,external_taxon_id),
  INDEX idx_taxonomy_reference_name(accepted_scientific_name),
  INDEX idx_taxonomy_reference_status(verification_status,source_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plant_knowledge_entries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plant_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  entry_type ENUM('IDENTIFICATION','MORPHOLOGY','DISTRIBUTION','HABITAT','CULTIVATION','USE','SEASONALITY','OTHER') NOT NULL,
  field_code VARCHAR(80) NULL,
  content_text MEDIUMTEXT NOT NULL,
  source_excerpt MEDIUMTEXT NULL,
  source_locator VARCHAR(500) NULL,
  license_code VARCHAR(120) NULL,
  attribution TEXT NULL,
  extraction_method VARCHAR(80) NOT NULL DEFAULT 'MANUAL',
  verification_status ENUM('CONFIRMED','NEEDS_REVIEW','UNKNOWN','DISPUTED') NOT NULL DEFAULT 'NEEDS_REVIEW',
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_knowledge_entry_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  CONSTRAINT fk_knowledge_entry_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_knowledge_entry_reviewer FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_knowledge_entry_public(plant_id,verification_status,entry_type),
  INDEX idx_knowledge_entry_source(source_id,verification_status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plant_image_references (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plant_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  image_type ENUM('WHOLE_PLANT','LEAF','FLOWER','FRUIT','CONE','BARK','HABITAT','OTHER') NOT NULL DEFAULT 'OTHER',
  source_url VARCHAR(1500) NOT NULL,
  author_name VARCHAR(240) NULL,
  license_code VARCHAR(120) NULL,
  attribution TEXT NULL,
  source_page_url VARCHAR(1500) NULL,
  can_redistribute BOOLEAN NOT NULL DEFAULT FALSE,
  verification_status ENUM('CONFIRMED','NEEDS_REVIEW','UNKNOWN','DISPUTED') NOT NULL DEFAULT 'NEEDS_REVIEW',
  retrieved_at DATETIME NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_image_reference_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  CONSTRAINT fk_image_reference_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_image_reference_reviewer FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_plant_image_reference(plant_id,source_url(500)),
  INDEX idx_image_reference_status(verification_status,license_code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS external_import_batches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_uuid CHAR(36) NOT NULL,
  provider_code VARCHAR(100) NOT NULL,
  source_id BIGINT NOT NULL,
  status ENUM('CREATED','RUNNING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED','ROLLED_BACK') NOT NULL DEFAULT 'CREATED',
  input_sha256 CHAR(64) NOT NULL,
  requested_count INT UNSIGNED NOT NULL DEFAULT 0,
  imported_count INT UNSIGNED NOT NULL DEFAULT 0,
  duplicate_count INT UNSIGNED NOT NULL DEFAULT 0,
  rejected_input_count INT UNSIGNED NOT NULL DEFAULT 0,
  error_summary TEXT NULL,
  requested_by BIGINT NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  rolled_back_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_import_batch_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_import_batch_user FOREIGN KEY(requested_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_import_batch_uuid(batch_uuid),
  INDEX idx_import_batch_provider(provider_code,status,created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS external_import_candidates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  candidate_key CHAR(64) NOT NULL,
  resolution ENUM('MATCH_CONFIRMED','MATCH_REVIEW_REQUIRED','NEW_TAXON_CANDIDATE') NOT NULL DEFAULT 'NEW_TAXON_CANDIDATE',
  record_kind ENUM('TAXONOMY','PLANT_KNOWLEDGE','IMAGE_REFERENCE') NOT NULL DEFAULT 'TAXONOMY',
  name_zh VARCHAR(160) NULL,
  scientific_name VARCHAR(220) NOT NULL,
  accepted_name VARCHAR(220) NULL,
  authorship VARCHAR(220) NULL,
  taxonomic_rank VARCHAR(60) NULL,
  family_name VARCHAR(160) NULL,
  genus_name VARCHAR(160) NULL,
  source_namespace VARCHAR(100) NULL,
  external_taxon_id VARCHAR(180) NULL,
  source_url VARCHAR(1500) NULL,
  license_code VARCHAR(120) NULL,
  attribution TEXT NULL,
  evidence_text MEDIUMTEXT NULL,
  content_payload JSON NOT NULL,
  verification_status ENUM('EXTRACTED','REVIEW_PENDING','CONFIRMED','REJECTED','READY_TO_PUBLISH','PUBLISHED') NOT NULL DEFAULT 'REVIEW_PENDING',
  linked_plant_id BIGINT NULL,
  retrieved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_import_candidate_batch FOREIGN KEY(batch_id) REFERENCES external_import_batches(id) ON DELETE RESTRICT,
  CONSTRAINT fk_import_candidate_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_import_candidate_plant FOREIGN KEY(linked_plant_id) REFERENCES plants(id) ON DELETE SET NULL,
  UNIQUE KEY uq_external_candidate_key(candidate_key),
  INDEX idx_import_candidate_review(verification_status,record_kind,source_id,created_at),
  INDEX idx_import_candidate_name(scientific_name),
  INDEX idx_import_candidate_external(source_namespace,external_taxon_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS external_import_reviews (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  candidate_id BIGINT NOT NULL,
  actor_user_id BIGINT NULL,
  decision ENUM('CONFIRM','REJECT','REQUEST_CHANGES','PUBLISH','ROLLBACK') NOT NULL,
  reason VARCHAR(2000) NOT NULL,
  before_data JSON NULL,
  after_data JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_import_review_candidate FOREIGN KEY(candidate_id) REFERENCES external_import_candidates(id) ON DELETE RESTRICT,
  CONSTRAINT fk_import_review_actor FOREIGN KEY(actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_import_review_candidate(candidate_id,created_at),
  INDEX idx_import_review_actor(actor_user_id,created_at)
) ENGINE=InnoDB;

INSERT IGNORE INTO plant_categories(category_key,category_type,label_zh,description,display_order) VALUES
 ('tree','GROWTH_FORM','乔木','有来源核验后用于浏览的植株形态标签。',10),
 ('shrub','GROWTH_FORM','灌木','有来源核验后用于浏览的植株形态标签。',20),
 ('herb','GROWTH_FORM','草本','有来源核验后用于浏览的植株形态标签。',30),
 ('vine','GROWTH_FORM','藤本','有来源核验后用于浏览的植株形态标签。',40),
 ('flower','APPLICATION','花卉','可与其他应用类别并存。',50),
 ('fruit_tree','APPLICATION','果树','可与其他应用类别并存。',60),
 ('food_crop','APPLICATION','粮食作物','可与其他应用类别并存。',70),
 ('vegetable','APPLICATION','蔬菜','可与其他应用类别并存。',80),
 ('medicinal','APPLICATION','药用植物','用途标签，不代表分类学等级。',90),
 ('aquatic','APPLICATION','水生植物','可与其他应用类别并存。',100),
 ('oil_crop','APPLICATION','油料植物','可与其他应用类别并存。',110),
 ('non_food_oil_crop','APPLICATION','非粮油料植物','课程专题标签；单株关联仍需来源确认。',120),
 ('ornamental','APPLICATION','园林观赏植物','可与其他应用类别并存。',130);

-- Reassert UTF-8 seed labels/descriptions on existing installations too. This
-- also repairs rows created by clients that did not issue SET NAMES utf8mb4.
UPDATE plant_categories SET
 label_zh=CASE category_key
  WHEN 'tree' THEN '乔木' WHEN 'shrub' THEN '灌木' WHEN 'herb' THEN '草本' WHEN 'vine' THEN '藤本'
  WHEN 'flower' THEN '花卉' WHEN 'fruit_tree' THEN '果树' WHEN 'food_crop' THEN '粮食作物'
  WHEN 'vegetable' THEN '蔬菜' WHEN 'medicinal' THEN '药用植物' WHEN 'aquatic' THEN '水生植物'
  WHEN 'oil_crop' THEN '油料植物' WHEN 'non_food_oil_crop' THEN '非粮油料植物'
  WHEN 'ornamental' THEN '园林观赏植物' ELSE label_zh END,
 description=CASE category_key
  WHEN 'tree' THEN '有来源核验后用于浏览的植株形态标签。'
  WHEN 'shrub' THEN '有来源核验后用于浏览的植株形态标签。'
  WHEN 'herb' THEN '有来源核验后用于浏览的植株形态标签。'
  WHEN 'vine' THEN '有来源核验后用于浏览的植株形态标签。'
  WHEN 'flower' THEN '可与其他应用类别并存。'
  WHEN 'fruit_tree' THEN '可与其他应用类别并存。'
  WHEN 'food_crop' THEN '可与其他应用类别并存。'
  WHEN 'vegetable' THEN '可与其他应用类别并存。'
  WHEN 'medicinal' THEN '用途标签，不代表分类学等级。'
  WHEN 'aquatic' THEN '可与其他应用类别并存。'
  WHEN 'oil_crop' THEN '可与其他应用类别并存。'
  WHEN 'non_food_oil_crop' THEN '课程专题标签；单株关联仍需来源确认。'
  WHEN 'ornamental' THEN '可与其他应用类别并存。'
  ELSE description END
WHERE category_key IN ('tree','shrub','herb','vine','flower','fruit_tree','food_crop','vegetable','medicinal','aquatic','oil_crop','non_food_oil_crop','ornamental');

INSERT IGNORE INTO plant_datasets(dataset_code,label_zh,description) VALUES
 ('COURSE_ORIGINAL','课程原始资料','原始课程 PDF 中的植物与科研采样数据。'),
 ('GENERAL_BOTANY','综合植物知识','经审核发布的通用植物知识。'),
 ('EXTERNAL_REFERENCE','外部参考资料','来源与许可已记录的外部参考数据。'),
 ('RESEARCH_EXTENSION','研究扩展资料','经审核加入的后续研究资料。');

UPDATE plant_datasets SET
 label_zh=CASE dataset_code
  WHEN 'COURSE_ORIGINAL' THEN '课程原始资料' WHEN 'GENERAL_BOTANY' THEN '综合植物知识'
  WHEN 'EXTERNAL_REFERENCE' THEN '外部参考资料' WHEN 'RESEARCH_EXTENSION' THEN '研究扩展资料' ELSE label_zh END,
 description=CASE dataset_code
  WHEN 'COURSE_ORIGINAL' THEN '原始课程 PDF 中的植物与科研采样数据。'
  WHEN 'GENERAL_BOTANY' THEN '经审核发布的通用植物知识。'
  WHEN 'EXTERNAL_REFERENCE' THEN '来源与许可已记录的外部参考数据。'
  WHEN 'RESEARCH_EXTENSION' THEN '经审核加入的后续研究资料。' ELSE description END
WHERE dataset_code IN ('COURSE_ORIGINAL','GENERAL_BOTANY','EXTERNAL_REFERENCE','RESEARCH_EXTENSION');

INSERT IGNORE INTO plant_dataset_relations(plant_id,dataset_id,source_id,verification_status,evidence_note)
SELECT p.id,d.id,s.id,'CONFIRMED','由原始 PDF source key、文件名和 SHA-256 关联；原课程记录未复制。'
FROM plants p
JOIN plant_datasets d ON d.dataset_code='COURSE_ORIGINAL'
JOIN plant_knowledge_sources s ON s.source_key='course-pdf-2026'
WHERE p.source_key LIKE 'pdf19-%' AND p.source_document IS NOT NULL AND p.source_pdf_sha256 IS NOT NULL;

UPDATE plant_dataset_relations r
JOIN plant_datasets d ON d.id=r.dataset_id AND d.dataset_code='COURSE_ORIGINAL'
SET r.evidence_note='依据原始 PDF 来源键、文件名和 SHA-256 关联；没有复制课程植物记录。'
WHERE r.verification_status='CONFIRMED';

-- The course collection is explicitly scoped to non-food oil/biodiesel plants.
-- Keep the relationship evidence-linked and unique; do not infer unrelated tags.
INSERT IGNORE INTO plant_category_relations(plant_id,category_id,source_id,source_excerpt,verification_status,reviewed_at)
SELECT p.id,c.id,s.id,'Member of the source collection explicitly scoped to non-food oil/biodiesel plants.',
       'CONFIRMED',CURRENT_TIMESTAMP
FROM plants p
JOIN plant_categories c ON c.category_key='non_food_oil_crop'
JOIN plant_knowledge_sources s ON s.source_key='course-pdf-2026'
WHERE p.source_key LIKE 'pdf19-%' AND p.source_document IS NOT NULL AND p.source_pdf_sha256 IS NOT NULL;

INSERT IGNORE INTO schema_migrations(version,script_name)
VALUES('20261014','20261014_plant_knowledge_expansion.sql');
