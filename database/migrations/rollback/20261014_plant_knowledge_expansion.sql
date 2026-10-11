USE nonfood_oil_plants;

-- Destructive rollback of the 3.2 extension schema only. Back up the database
-- first. This removes category/dataset assignments, external candidates,
-- taxonomy references, and all 3.2 review history; it does not delete rows in
-- plants, plant_oil_samples, plant_images, fatty_acid_components, or users.
DROP TABLE IF EXISTS external_import_reviews;
DROP TABLE IF EXISTS external_import_candidates;
DROP TABLE IF EXISTS external_import_batches;
DROP TABLE IF EXISTS plant_image_references;
DROP TABLE IF EXISTS plant_knowledge_entries;
DROP TABLE IF EXISTS plant_taxonomy_references;
DROP TABLE IF EXISTS plant_external_identifiers;
DROP TABLE IF EXISTS plant_dataset_relations;
DROP TABLE IF EXISTS plant_datasets;
DROP TABLE IF EXISTS plant_category_relations;
DROP TABLE IF EXISTS plant_categories;
DELETE FROM schema_migrations WHERE version='20261014';
