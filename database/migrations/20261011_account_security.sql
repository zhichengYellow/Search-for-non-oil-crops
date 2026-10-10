USE nonfood_oil_plants;

ALTER TABLE users ADD COLUMN email_verified_at DATETIME NULL AFTER email;

CREATE TABLE auth_email_challenges (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  challenge_key CHAR(36) NOT NULL UNIQUE,
  email VARCHAR(160) NOT NULL,
  purpose ENUM('REGISTRATION','VERIFY_EMAIL','PASSWORD_RESET') NOT NULL,
  user_id BIGINT NULL,
  code_digest BINARY(32) NOT NULL,
  attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
  expires_at DATETIME NOT NULL,
  consumed_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_email_challenge_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_email_challenge_lookup(email,purpose,created_at),
  INDEX idx_email_challenge_user(user_id,purpose,consumed_at)
) ENGINE=InnoDB;

CREATE TABLE auth_login_attempts (
  subject_hash BINARY(32) PRIMARY KEY,
  failed_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  window_started_at DATETIME NOT NULL,
  locked_until DATETIME NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_auth_attempt_lock(locked_until,updated_at)
) ENGINE=InnoDB;

CREATE TABLE auth_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,
  attempted_username VARCHAR(32) NULL,
  event_type ENUM('REGISTER_SUCCESS','REGISTER_FAILURE','LOGIN_SUCCESS','LOGIN_FAILURE','LOGIN_THROTTLED','EMAIL_CODE_REQUEST','EMAIL_VERIFY_SUCCESS','PASSWORD_RESET_SUCCESS') NOT NULL,
  remote_addr VARCHAR(45) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_auth_event_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_auth_event_user(user_id,created_at),
  INDEX idx_auth_event_type(event_type,created_at)
) ENGINE=InnoDB;
