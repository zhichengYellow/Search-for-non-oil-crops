USE nonfood_oil_plants;

-- Additive PlantScope ontology and evidence tables. Existing course plant/sample
-- tables and rows are intentionally untouched by this migration.
CREATE TABLE IF NOT EXISTS trait_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(48) NOT NULL UNIQUE,
  label_zh VARCHAR(80) NOT NULL,
  display_order SMALLINT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plant_knowledge_sources (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_key VARCHAR(160) NOT NULL UNIQUE,
  source_type ENUM('COURSE_PDF','EXTERNAL_REFERENCE','LITERATURE','USER_OBSERVATION','ADMIN_NOTE') NOT NULL,
  source_name VARCHAR(240) NOT NULL,
  source_url VARCHAR(1000) NULL,
  citation TEXT NULL,
  license_code VARCHAR(80) NULL,
  retrieved_at DATETIME NULL,
  verification_status ENUM('CONFIRMED','NEEDS_REVIEW','UNKNOWN','DISPUTED') NOT NULL DEFAULT 'NEEDS_REVIEW',
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_knowledge_source_user FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_knowledge_source_type(source_type, verification_status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plant_traits (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL UNIQUE,
  label_zh VARCHAR(100) NOT NULL,
  search_weight DECIMAL(5,2) NOT NULL DEFAULT 1.00,
  display_order SMALLINT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_trait_category FOREIGN KEY(category_id) REFERENCES trait_categories(id) ON DELETE RESTRICT,
  INDEX idx_trait_category(category_id, is_active)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS trait_options (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trait_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  label_zh VARCHAR(100) NOT NULL,
  display_order SMALLINT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_trait_option_trait FOREIGN KEY(trait_id) REFERENCES plant_traits(id) ON DELETE RESTRICT,
  UNIQUE KEY uq_trait_option_code(trait_id, code),
  INDEX idx_trait_option_lookup(trait_id, is_active, display_order)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS plant_trait_values (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plant_id BIGINT NOT NULL,
  trait_id BIGINT NOT NULL,
  option_id BIGINT NULL,
  value_text VARCHAR(500) NULL,
  numeric_value DECIMAL(12,3) NULL,
  unit VARCHAR(32) NULL,
  verification_status ENUM('CONFIRMED','NEEDS_REVIEW','UNKNOWN','DISPUTED') NOT NULL DEFAULT 'NEEDS_REVIEW',
  source_id BIGINT NOT NULL,
  source_page VARCHAR(80) NULL,
  source_excerpt TEXT NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_trait_value_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE,
  CONSTRAINT fk_trait_value_trait FOREIGN KEY(trait_id) REFERENCES plant_traits(id) ON DELETE RESTRICT,
  CONSTRAINT fk_trait_value_option FOREIGN KEY(option_id) REFERENCES trait_options(id) ON DELETE RESTRICT,
  CONSTRAINT fk_trait_value_source FOREIGN KEY(source_id) REFERENCES plant_knowledge_sources(id) ON DELETE RESTRICT,
  CONSTRAINT fk_trait_value_reviewer FOREIGN KEY(reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_trait_value_search(trait_id, option_id, verification_status, plant_id),
  INDEX idx_trait_value_plant(plant_id, verification_status),
  INDEX idx_trait_value_source(source_id)
) ENGINE=InnoDB;

INSERT IGNORE INTO trait_categories(code,label_zh,display_order) VALUES
 ('plant_form','整体形态',10),('plant_size','植株大小',20),('leaf','叶片',30),
 ('flower','花朵',40),('fruit','果实或球果',50),('bark','树皮',60),
 ('habitat','生长环境与分布',70),('phenology','季节表现',80);

INSERT IGNORE INTO plant_knowledge_sources(source_key,source_type,source_name,citation,license_code,verification_status)
VALUES ('course-pdf-2026','COURSE_PDF','中国非粮生物柴油植物课程设计资料',
 '课程资料原始扫描 PDF，docs/课程设计资料.pdf，共 19 页；仅作为可追溯的原始来源记录，具体字段仍按页面证据分别核验。',
 'UNKNOWN','CONFIRMED');

INSERT IGNORE INTO plant_traits(category_id,code,label_zh,search_weight,display_order)
SELECT c.id,v.code,v.label_zh,v.weight,v.sort_order
FROM trait_categories c JOIN (
 SELECT 'plant_form' category_code,'plant_form' code,'植株类型' label_zh,1.10 weight,10 sort_order UNION ALL
 SELECT 'plant_size','plant_height','植株高度范围',0.80,10 UNION ALL
 SELECT 'leaf','leaf_persistence','常绿或落叶',1.00,10 UNION ALL
 SELECT 'leaf','leaf_shape','叶片形状',1.20,20 UNION ALL
 SELECT 'leaf','leaf_color','叶片颜色',0.55,30 UNION ALL
 SELECT 'leaf','leaf_margin','叶缘形态',0.60,40 UNION ALL
 SELECT 'leaf','leaf_arrangement','叶片排列',0.60,50 UNION ALL
 SELECT 'flower','flower_color','花朵颜色',0.60,10 UNION ALL
 SELECT 'flower','flower_shape','花朵形状',0.55,20 UNION ALL
 SELECT 'fruit','fruit_shape','果实或球果形状',0.85,10 UNION ALL
 SELECT 'fruit','fruit_color','果实颜色',0.55,20 UNION ALL
 SELECT 'fruit','fruit_size','果实大小',0.40,30 UNION ALL
 SELECT 'bark','bark_texture','树皮颜色或纹理',0.35,10 UNION ALL
 SELECT 'habitat','growth_habitat','生长环境',0.75,10 UNION ALL
 SELECT 'habitat','distribution_region','常见分布区域',0.55,20 UNION ALL
 SELECT 'phenology','autumn_leaf_color','秋季叶片颜色',0.80,10 UNION ALL
 SELECT 'phenology','winter_appearance','冬季表现',0.75,20 UNION ALL
 SELECT 'phenology','flowering_season','开花季节',0.40,30
) v ON v.category_code=c.code;

INSERT IGNORE INTO trait_options(trait_id,code,label_zh,display_order)
SELECT t.id,v.option_code,v.label_zh,v.sort_order FROM plant_traits t JOIN (
 SELECT 'plant_form' trait_code,'tree' option_code,'乔木' label_zh,10 sort_order UNION ALL
 SELECT 'plant_form','shrub','灌木',20 UNION ALL SELECT 'plant_form','herb','草本',30 UNION ALL
 SELECT 'plant_form','vine','藤本',40 UNION ALL SELECT 'plant_form','aquatic','水生植物',50 UNION ALL
 SELECT 'plant_height','low_under_1m','低矮（约 1 米以下）',10 UNION ALL
 SELECT 'plant_height','medium_1_to_5m','中等（约 1–5 米）',20 UNION ALL
 SELECT 'plant_height','tall_over_5m','较高（约 5 米以上）',30 UNION ALL
 SELECT 'leaf_persistence','evergreen','常绿',10 UNION ALL SELECT 'leaf_persistence','deciduous','落叶',20 UNION ALL
 SELECT 'leaf_persistence','semi_evergreen','半常绿',30 UNION ALL
 SELECT 'leaf_shape','needle','针形',10 UNION ALL SELECT 'leaf_shape','fan_shaped','扇形',20 UNION ALL
 SELECT 'leaf_shape','linear','线形',30 UNION ALL SELECT 'leaf_shape','lanceolate','披针形',40 UNION ALL
 SELECT 'leaf_shape','ovate','卵形',50 UNION ALL SELECT 'leaf_shape','elliptic','椭圆形',60 UNION ALL
 SELECT 'leaf_shape','heart_shaped','心形',70 UNION ALL SELECT 'leaf_shape','palmate_lobed','掌状裂',80 UNION ALL
 SELECT 'leaf_shape','pinnate_compound','羽状复叶',90 UNION ALL SELECT 'leaf_shape','scale_like','鳞形',100 UNION ALL
 SELECT 'leaf_shape','other','其他或资料未分类',110 UNION ALL
 SELECT 'leaf_color','green','绿色',10 UNION ALL SELECT 'leaf_color','blue_green','蓝绿色',20 UNION ALL
 SELECT 'leaf_color','yellow','黄色',30 UNION ALL SELECT 'leaf_color','reddish','红色或红褐色',40 UNION ALL
 SELECT 'leaf_color','silver','银灰色',50 UNION ALL SELECT 'leaf_color','variegated','斑叶',60 UNION ALL
 SELECT 'leaf_color','other','其他颜色',70 UNION ALL
 SELECT 'leaf_margin','entire','全缘',10 UNION ALL SELECT 'leaf_margin','serrated','锯齿缘',20 UNION ALL
 SELECT 'leaf_margin','lobed','分裂或浅裂',30 UNION ALL SELECT 'leaf_margin','wavy','波状缘',40 UNION ALL
 SELECT 'leaf_arrangement','alternate','互生',10 UNION ALL SELECT 'leaf_arrangement','opposite','对生',20 UNION ALL
 SELECT 'leaf_arrangement','whorled','轮生',30 UNION ALL SELECT 'leaf_arrangement','basal','基生',40 UNION ALL
 SELECT 'flower_color','white','白色',10 UNION ALL SELECT 'flower_color','yellow','黄色',20 UNION ALL
 SELECT 'flower_color','pink','粉色',30 UNION ALL SELECT 'flower_color','red','红色',40 UNION ALL
 SELECT 'flower_color','purple','紫色',50 UNION ALL SELECT 'flower_color','green','绿色',60 UNION ALL
 SELECT 'flower_shape','radial','辐射状',10 UNION ALL SELECT 'flower_shape','tubular','管状',20 UNION ALL
 SELECT 'flower_shape','cup','杯状',30 UNION ALL SELECT 'flower_shape','cluster','成簇或头状',40 UNION ALL
 SELECT 'flower_shape','other','其他形状',50 UNION ALL
 SELECT 'fruit_shape','cone','球果或松塔状',10 UNION ALL SELECT 'fruit_shape','pod','荚果状',20 UNION ALL
 SELECT 'fruit_shape','capsule','蒴果状',30 UNION ALL SELECT 'fruit_shape','berry','浆果状',40 UNION ALL
 SELECT 'fruit_shape','drupe','核果状',50 UNION ALL SELECT 'fruit_shape','nut','坚果状',60 UNION ALL
 SELECT 'fruit_shape','samara','翅果状',70 UNION ALL SELECT 'fruit_shape','elongated','细长形',80 UNION ALL
 SELECT 'fruit_shape','other','其他形状',90 UNION ALL
 SELECT 'fruit_color','green','绿色',10 UNION ALL SELECT 'fruit_color','brown','棕色',20 UNION ALL
 SELECT 'fruit_color','red','红色',30 UNION ALL SELECT 'fruit_color','yellow','黄色',40 UNION ALL
 SELECT 'fruit_color','black','黑色',50 UNION ALL SELECT 'fruit_color','other','其他颜色',60 UNION ALL
 SELECT 'fruit_size','tiny_under_1cm','很小（约 1 厘米以下）',10 UNION ALL
 SELECT 'fruit_size','small_1_to_3cm','小（约 1–3 厘米）',20 UNION ALL
 SELECT 'fruit_size','medium_3_to_10cm','中（约 3–10 厘米）',30 UNION ALL
 SELECT 'fruit_size','large_over_10cm','大（约 10 厘米以上）',40 UNION ALL
 SELECT 'bark_texture','smooth','较光滑',10 UNION ALL SELECT 'bark_texture','fissured','纵裂或沟裂',20 UNION ALL
 SELECT 'bark_texture','peeling','片状剥落',30 UNION ALL SELECT 'bark_texture','fibrous','纤维状',40 UNION ALL
 SELECT 'growth_habitat','forest','森林或林地',10 UNION ALL SELECT 'growth_habitat','grassland','草地或灌丛',20 UNION ALL
 SELECT 'growth_habitat','wetland','湿地或水边',30 UNION ALL SELECT 'growth_habitat','coastal','海岸或盐碱地',40 UNION ALL
 SELECT 'growth_habitat','mountain','山地',50 UNION ALL SELECT 'growth_habitat','dryland','干旱或半干旱环境',60 UNION ALL
 SELECT 'distribution_region','east_asia','东亚',10 UNION ALL SELECT 'distribution_region','tropical_asia','热带亚洲',20 UNION ALL
 SELECT 'distribution_region','north_america','北美洲',30 UNION ALL SELECT 'distribution_region','europe','欧洲',40 UNION ALL
 SELECT 'distribution_region','africa','非洲',50 UNION ALL SELECT 'distribution_region','other','其他区域',60 UNION ALL
 SELECT 'autumn_leaf_color','yellow','秋季转黄',10 UNION ALL SELECT 'autumn_leaf_color','orange','秋季转橙',20 UNION ALL
 SELECT 'autumn_leaf_color','red','秋季转红',30 UNION ALL SELECT 'autumn_leaf_color','brown','秋季转褐',40 UNION ALL
 SELECT 'winter_appearance','green','冬季保持绿色',10 UNION ALL SELECT 'winter_appearance','bare','冬季落叶或裸枝',20 UNION ALL
 SELECT 'winter_appearance','fruit_visible','冬季仍可见果实',30 UNION ALL
 SELECT 'flowering_season','spring','春季开花',10 UNION ALL SELECT 'flowering_season','summer','夏季开花',20 UNION ALL
 SELECT 'flowering_season','autumn','秋季开花',30 UNION ALL SELECT 'flowering_season','winter','冬季开花',40
) v ON v.trait_code=t.code;
