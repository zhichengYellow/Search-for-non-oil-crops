-- Read-only PlantScope 3.0 baseline. Run against the selected MySQL database.
-- This file contains SELECT statements only.

SELECT DATABASE() AS database_name, VERSION() AS mysql_version,
       @@character_set_server AS server_charset,
       @@collation_server AS server_collation;

SELECT f.family_name_latin AS family, COUNT(p.id) AS plant_count
FROM plant_families f
LEFT JOIN plant_genera g ON g.family_id = f.id
LEFT JOIN plants p ON p.genus_id = g.id
GROUP BY f.id, f.family_name_latin
ORDER BY plant_count DESC, family;

SELECT g.genus_name_latin AS genus, COUNT(p.id) AS plant_count
FROM plant_genera g
LEFT JOIN plants p ON p.genus_id = g.id
GROUP BY g.id, g.genus_name_latin
ORDER BY plant_count DESC, genus;

SELECT 'plants_total' AS metric, COUNT(*) AS value FROM plants
UNION ALL SELECT 'plants_with_pdf_source', COUNT(*) FROM plants
 WHERE source_key IS NOT NULL AND source_document IS NOT NULL AND source_pdf_sha256 IS NOT NULL
UNION ALL SELECT 'plants_with_source_page', COUNT(*) FROM plants
 WHERE source_pages IS NOT NULL AND source_pages <> ''
UNION ALL SELECT 'plants_with_verified_printed_heading_name', COUNT(*) FROM plants
 WHERE scientific_name_status = 'VERIFIED_FROM_PRINTED_HEADING'
UNION ALL SELECT 'plants_with_any_image', COUNT(DISTINCT plant_id) FROM plant_images
UNION ALL SELECT 'images_total', COUNT(*) FROM plant_images
UNION ALL SELECT 'images_with_pdf_page_and_crop_bbox', COUNT(*) FROM plant_images
 WHERE source_pdf_page IS NOT NULL AND source_crop_bbox IS NOT NULL
UNION ALL SELECT 'samples_total', COUNT(*) FROM plant_oil_samples
UNION ALL SELECT 'samples_with_verified_oil_value', COUNT(*) FROM plant_oil_samples
 WHERE oil_content_status = 'VERIFIED' AND oil_content_pct IS NOT NULL
UNION ALL SELECT 'samples_with_missing_or_unreadable_oil', COUNT(*) FROM plant_oil_samples
 WHERE oil_content_status = 'MISSING_OR_UNREADABLE'
UNION ALL SELECT 'plants_with_at_least_one_verified_oil_value', COUNT(DISTINCT plant_id)
 FROM plant_oil_samples WHERE oil_content_status = 'VERIFIED' AND oil_content_pct IS NOT NULL
UNION ALL SELECT 'fatty_acid_component_rows', COUNT(*) FROM fatty_acid_components
UNION ALL SELECT 'verified_fatty_acid_component_rows', COUNT(*) FROM fatty_acid_components
 WHERE verification_status = 'VERIFIED'
UNION ALL SELECT 'samples_with_verified_fatty_acid', COUNT(DISTINCT s.id)
 FROM plant_oil_samples s JOIN fatty_acid_components c ON c.sample_id = s.id
 WHERE c.verification_status = 'VERIFIED'
UNION ALL SELECT 'trait_value_rows', COUNT(*) FROM plant_trait_values
UNION ALL SELECT 'confirmed_trait_value_rows', COUNT(*) FROM plant_trait_values
 WHERE verification_status = 'CONFIRMED'
UNION ALL SELECT 'extracted_trait_candidates', COUNT(*) FROM plant_trait_values
 WHERE verification_status = 'EXTRACTED'
UNION ALL SELECT 'trait_candidates_pending_review', COUNT(*) FROM plant_trait_values
 WHERE verification_status IN ('REVIEW_PENDING','NEEDS_REVIEW')
UNION ALL SELECT 'plants_with_confirmed_trait', COUNT(DISTINCT plant_id)
 FROM plant_trait_values WHERE verification_status = 'CONFIRMED'
UNION ALL SELECT 'fatty_acid_candidates_total', COUNT(*) FROM fatty_acid_candidates
UNION ALL SELECT 'fatty_acid_candidates_review_pending', COUNT(*) FROM fatty_acid_candidates
 WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING')
UNION ALL SELECT 'fatty_acid_candidates_confirmed', COUNT(*) FROM fatty_acid_candidates
 WHERE verification_status = 'CONFIRMED'
UNION ALL SELECT 'fatty_acid_candidates_with_reviewed_unit', COUNT(*) FROM fatty_acid_candidates
 WHERE reviewed_unit IS NOT NULL
UNION ALL SELECT 'external_taxon_candidates_total', COUNT(*) FROM external_taxon_candidates
UNION ALL SELECT 'external_taxon_candidates_pending', COUNT(*) FROM external_taxon_candidates
 WHERE verification_status IN ('EXTRACTED','REVIEW_PENDING')
UNION ALL SELECT 'knowledge_sources_total', COUNT(*) FROM plant_knowledge_sources
UNION ALL SELECT 'knowledge_sources_pending_review', COUNT(*) FROM plant_knowledge_sources
 WHERE verification_status = 'NEEDS_REVIEW'
UNION ALL SELECT 'plant_records_pending_review', COUNT(*) FROM plants
 WHERE verification_status = 'NEEDS_REVIEW'
UNION ALL SELECT 'sample_records_pending_review', COUNT(*) FROM plant_oil_samples
 WHERE verification_status = 'NEEDS_REVIEW';

SELECT 'oil_value_plant_coverage_pct' AS metric,
       ROUND(100 * COUNT(DISTINCT CASE WHEN s.oil_content_status = 'VERIFIED'
             AND s.oil_content_pct IS NOT NULL THEN s.plant_id END)
             / NULLIF((SELECT COUNT(*) FROM plants), 0), 2) AS percent
FROM plant_oil_samples s
UNION ALL
SELECT 'oil_value_sample_coverage_pct',
       ROUND(100 * SUM(oil_content_status = 'VERIFIED' AND oil_content_pct IS NOT NULL)
             / NULLIF(COUNT(*), 0), 2)
FROM plant_oil_samples
UNION ALL
SELECT 'image_plant_coverage_pct',
       ROUND(100 * COUNT(DISTINCT i.plant_id) / NULLIF((SELECT COUNT(*) FROM plants), 0), 2)
FROM plant_images i
UNION ALL
SELECT 'confirmed_trait_plant_coverage_pct',
       ROUND(100 * COUNT(DISTINCT v.plant_id) / NULLIF((SELECT COUNT(*) FROM plants), 0), 2)
FROM plant_trait_values v WHERE v.verification_status = 'CONFIRMED'
UNION ALL
SELECT 'verified_fatty_acid_plant_coverage_pct',
       ROUND(100 * COUNT(DISTINCT s.plant_id) / NULLIF((SELECT COUNT(*) FROM plants), 0), 2)
FROM plant_oil_samples s JOIN fatty_acid_components c ON c.sample_id = s.id
WHERE c.verification_status = 'VERIFIED';

SELECT 'plant_missing_pdf_source' AS integrity_check, COUNT(*) AS issue_count
FROM plants WHERE source_key IS NULL OR source_document IS NULL OR source_pdf_sha256 IS NULL
UNION ALL SELECT 'sample_missing_plant', COUNT(*) FROM plant_oil_samples s
 LEFT JOIN plants p ON p.id = s.plant_id WHERE p.id IS NULL
UNION ALL SELECT 'sample_missing_source_key_or_page', COUNT(*) FROM plant_oil_samples
 WHERE source_key IS NULL OR source_pdf_page IS NULL
UNION ALL SELECT 'image_missing_plant_or_page_bbox', COUNT(*) FROM plant_images i
 LEFT JOIN plants p ON p.id = i.plant_id
 WHERE p.id IS NULL OR i.source_pdf_page IS NULL OR i.source_crop_bbox IS NULL
UNION ALL SELECT 'verified_oil_missing_value', COUNT(*) FROM plant_oil_samples
 WHERE oil_content_status = 'VERIFIED' AND oil_content_pct IS NULL
UNION ALL SELECT 'missing_oil_with_value', COUNT(*) FROM plant_oil_samples
 WHERE oil_content_status = 'MISSING_OR_UNREADABLE' AND oil_content_pct IS NOT NULL
UNION ALL SELECT 'verified_fatty_acid_missing_value', COUNT(*) FROM fatty_acid_components
 WHERE verification_status = 'VERIFIED' AND percentage IS NULL
UNION ALL SELECT 'confirmed_trait_without_source_excerpt', COUNT(*) FROM plant_trait_values
 WHERE verification_status = 'CONFIRMED' AND (source_excerpt IS NULL OR source_excerpt = '')
UNION ALL SELECT 'fatty_candidate_orphan_sample_or_source', COUNT(*) FROM fatty_acid_candidates c
 LEFT JOIN plant_oil_samples s ON s.id=c.sample_id
 LEFT JOIN plant_knowledge_sources k ON k.id=c.source_id
 WHERE s.id IS NULL OR k.id IS NULL
UNION ALL SELECT 'fatty_candidate_confirmed_without_reviewed_unit', COUNT(*) FROM fatty_acid_candidates
 WHERE verification_status='CONFIRMED' AND (reviewed_numeric_value IS NULL OR reviewed_unit IS NULL OR review_source_excerpt IS NULL)
UNION ALL SELECT 'trait_candidate_duplicate_source_key', COUNT(*) FROM (
 SELECT source_key FROM plant_trait_values WHERE source_key IS NOT NULL GROUP BY source_key HAVING COUNT(*)>1
) d
UNION ALL SELECT 'candidate_import_event_orphan_entity', COUNT(*) FROM knowledge_review_events e
 LEFT JOIN plant_trait_values t ON e.entity_type='plant_trait_value' AND t.id=e.entity_id
 LEFT JOIN fatty_acid_candidates f ON e.entity_type='fatty_acid_candidate' AND f.id=e.entity_id
 LEFT JOIN external_taxon_candidates x ON e.entity_type='external_taxon_candidate' AND x.id=e.entity_id
 WHERE e.action='IMPORT_CANDIDATE' AND ((e.entity_type='plant_trait_value' AND t.id IS NULL)
   OR (e.entity_type='fatty_acid_candidate' AND f.id IS NULL)
   OR (e.entity_type='external_taxon_candidate' AND x.id IS NULL));

SELECT version,script_name,applied_at FROM schema_migrations ORDER BY version;

SELECT COUNT(*) AS application_table_count
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_type='BASE TABLE';

SELECT source_key,verification_status FROM plant_knowledge_sources
WHERE source_key IN ('course-pdf-2026','gbif-species-api-v2') ORDER BY source_key;

SELECT TABLE_NAME AS migration_tracking_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('schema_migrations', 'db_migrations', 'flyway_schema_history');
