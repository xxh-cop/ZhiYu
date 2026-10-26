CREATE TABLE IF NOT EXISTS group_message (
 id BIGINT NOT NULL PRIMARY KEY,
 group_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 content TEXT NOT NULL,
 create_time DATETIME NOT NULL,
 update_time DATETIME NOT NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 KEY idx_group_message (group_id,deleted,id)
);
