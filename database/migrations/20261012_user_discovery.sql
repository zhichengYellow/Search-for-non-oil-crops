USE nonfood_oil_plants;

CREATE TABLE user_search_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  search_mode ENUM('BASIC','ADVANCED','DESCRIPTION','TRAITS') NOT NULL,
  search_summary VARCHAR(500) NOT NULL,
  search_path TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_search_history_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_search_history_owner_time(user_id,created_at)
) ENGINE=InnoDB;

CREATE TABLE user_plant_history (
  user_id BIGINT NOT NULL,
  plant_id BIGINT NOT NULL,
  last_viewed DATETIME NOT NULL,
  view_count INT UNSIGNED NOT NULL DEFAULT 1,
  PRIMARY KEY(user_id,plant_id),
  INDEX idx_plant_history_recent(user_id,last_viewed),
  CONSTRAINT fk_plant_history_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_plant_history_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE plant_daily_views (
  plant_id BIGINT NOT NULL,
  view_date DATE NOT NULL,
  view_count INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY(plant_id,view_date),
  INDEX idx_plant_views_date(view_date,view_count),
  CONSTRAINT fk_plant_views_plant FOREIGN KEY(plant_id) REFERENCES plants(id) ON DELETE CASCADE
) ENGINE=InnoDB;
