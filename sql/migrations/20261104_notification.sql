-- 通知系统（2026-11-04）：用户通知表。
-- 只新增表，不修改现有业务表；CREATE TABLE IF NOT EXISTS 可重复执行（重复执行安全）。
-- 已有数据库执行本脚本即可获得 notification 表；空库由 schema.sql 全量建表。
CREATE TABLE IF NOT EXISTS notification (
  id            BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL COMMENT '接收者（本人数据隔离）',
  type          VARCHAR(30)  NOT NULL COMMENT 'WORK_AUDIT/COMMENT_AUDIT/BOOKING_REPLY/GROUP_JOIN/CARE_REMINDER',
  title         VARCHAR(120) NOT NULL,
  content       VARCHAR(500) DEFAULT NULL COMMENT '脱敏正文；关怀类只含引导语，不含敏感内容',
  ref_type      VARCHAR(30)  DEFAULT NULL COMMENT '关联对象类型：work/comment/booking/group/care_reminder',
  ref_id        BIGINT       DEFAULT NULL COMMENT '关联对象 id（雪花，前端按字符串处理）',
  dedupe_key    VARCHAR(80)  NOT NULL COMMENT '幂等去重键：同类同对象同结果不重复刷屏',
  read_flag     TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  create_time   DATETIME     DEFAULT NULL,
  update_time   DATETIME     DEFAULT NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_dedupe (user_id,type,ref_type,ref_id,dedupe_key),
  KEY idx_notification_user_read (user_id,read_flag,id),
  KEY idx_notification_user_type (user_id,type,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知表';
