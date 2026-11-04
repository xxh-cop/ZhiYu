-- N2 导出审计：admin_export_log（2026-11-04）。
-- 只新增表；CREATE TABLE IF NOT EXISTS 幂等，重复执行安全。
-- N1 历史数据决策（同期记录于此，便于审计追溯）：
--   2026-11-04 起新发布作品（text/image/video）一律 status=0「先审后发」；
--   2026-11-04 前已公开（status=1）的历史作品【不回滚、不批量改状态】，
--   仅对新发布生效。本迁移不修改任何既有业务行。
CREATE TABLE IF NOT EXISTS admin_export_log (
  id            BIGINT       NOT NULL,
  actor_id      BIGINT       NOT NULL COMMENT '导出人（管理员）',
  actor_role    TINYINT      NOT NULL COMMENT '导出时角色',
  target_type   VARCHAR(20)  NOT NULL COMMENT 'users/works/comments',
  filter_summary VARCHAR(500) DEFAULT NULL COMMENT '筛选条件脱敏摘要',
  field_list    VARCHAR(500) NOT NULL COMMENT '导出字段（黑名单校验后）',
  record_count  INT          NOT NULL DEFAULT 0,
  purpose       VARCHAR(200) NOT NULL COMMENT '导出用途（必填）',
  batch_no      VARCHAR(32)  NOT NULL COMMENT '导出批次号（文件名/文件头引用）',
  alert_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1超阈值告警',
  create_time   DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_export_batch (batch_no),
  KEY idx_export_actor (actor_id,create_time),
  KEY idx_export_target (target_type,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出审计留痕（只追加）';
