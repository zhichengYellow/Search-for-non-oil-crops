USE nonfood_oil_plants;
SET NAMES utf8mb4;

-- Repair only display labels that were inserted by the 20261013 migration
-- through a non-UTF-8 client. Trait codes, evidence, and review states stay intact.
UPDATE trait_categories SET label_zh='球果' WHERE code='cone';
UPDATE plant_traits SET label_zh='球果形态' WHERE code='cone_shape';
UPDATE plant_traits SET label_zh='树皮颜色' WHERE code='bark_color';

UPDATE trait_options o JOIN plant_traits t ON t.id=o.trait_id
SET o.label_zh='近圆形'
WHERE t.code='fruit_shape' AND o.code='round';
UPDATE trait_options o JOIN plant_traits t ON t.id=o.trait_id
SET o.label_zh='粗糙'
WHERE t.code='bark_texture' AND o.code='rough';
UPDATE trait_options o JOIN plant_traits t ON t.id=o.trait_id
SET o.label_zh='球果'
WHERE t.code='cone_shape' AND o.code='cone';
UPDATE trait_options o JOIN plant_traits t ON t.id=o.trait_id
SET o.label_zh=CASE o.code
  WHEN 'gray' THEN '灰色或灰褐色'
  WHEN 'brown' THEN '褐色'
  WHEN 'red_brown' THEN '红褐色'
  WHEN 'dark' THEN '深色'
  WHEN 'other' THEN '其他颜色'
  ELSE o.label_zh
END
WHERE t.code='bark_color' AND o.code IN ('gray','brown','red_brown','dark','other');

INSERT INTO schema_migrations(version,script_name)
VALUES('20261015','20261015_fix_trait_labels.sql')
ON DUPLICATE KEY UPDATE script_name=VALUES(script_name);
