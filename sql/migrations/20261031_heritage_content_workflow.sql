-- 非遗内容工作流：草稿仍在 runtime_config（heritage_catalog_draft），
-- 本表只追加已发布版本快照，支持版本历史与回滚。可重复执行。
CREATE TABLE IF NOT EXISTS heritage_catalog_version (
  id          BIGINT      NOT NULL,
  version_no  INT         NOT NULL COMMENT '发布版本号，从 1 递增',
  payload     TEXT        NOT NULL COMMENT '完整目录快照(JSON)',
  note        VARCHAR(200) DEFAULT NULL COMMENT '发布说明',
  actor_id    BIGINT      NOT NULL COMMENT '发布管理员',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_heritage_version (version_no),
  KEY idx_heritage_version_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非遗目录发布版本';
