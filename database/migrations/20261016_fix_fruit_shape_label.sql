USE nonfood_oil_plants;
SET NAMES utf8mb4;

-- Repair one display label that remained mojibaked after the 20261015 repair.
-- This changes trait metadata only; plant records, evidence, and review states stay intact.
UPDATE plant_traits
SET label_zh='果实形状'
WHERE code='fruit_shape' AND label_zh<>'果实形状';

INSERT INTO schema_migrations(version,script_name)
VALUES('20261016','20261016_fix_fruit_shape_label.sql')
ON DUPLICATE KEY UPDATE script_name=VALUES(script_name);
