-- Additive migration; no existing rows are modified.
USE zhiyu;

-- Growth modules: encrypted personal records, scheduled programs and capacity-safe bookings.
CREATE TABLE IF NOT EXISTS wellbeing_record (
 id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, kind VARCHAR(24) NOT NULL,
 record_key VARCHAR(100) NOT NULL, payload TEXT NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_wellbeing_record (user_id, kind, record_key),
 KEY idx_wellbeing_owner (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS learning_program (
 id BIGINT PRIMARY KEY, owner_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL,
 title VARCHAR(100) NOT NULL, description TEXT NOT NULL, location VARCHAR(200) NOT NULL,
 co_leader VARCHAR(100) NOT NULL, materials VARCHAR(1000) NOT NULL,
 start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
 capacity INT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 KEY idx_program_schedule (status,start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS program_booking (
 id BIGINT PRIMARY KEY, program_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'BOOKED',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_program_member (program_id,user_id), KEY idx_booking_owner (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS craft_safety (
 id BIGINT PRIMARY KEY, project_id BIGINT NOT NULL UNIQUE, medium VARCHAR(20) NOT NULL,
 level VARCHAR(16) NOT NULL, hazards VARCHAR(300) NOT NULL, guidance VARCHAR(1000) NOT NULL,
 source VARCHAR(300) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
