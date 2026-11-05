-- ============================================================
-- 20261105_craft_session_event.sql
-- 目的：创作会话行为事件表（心流度量修复配套数据源，2026-11-05）。
--   - 补齐「步骤时间线 / 焦躁双信号 / 疑似离席 / 呼吸响应 / 结束事件」等
--     此前不可得指标的数据源；
--   - 焦躁双信号分开记：dwell_stagnation（同一步骤反复停留）与
--     step_backtrack（步骤回退）分别记录，产品裁决前不合并；
--   - 写入幂等：(session_id, client_seq) 唯一键去重，服务端派生事件
--     client_seq 为 NULL（MySQL 唯一索引允许多个 NULL，不参与去重）。
--
-- 备份（执行前）：
--   mysqldump -u<user> -p <db> craft_session > backup_craft_session_20261105.sql
--   （本迁移只新建表，不改动既有表；备份仅作常规留档。）
--
-- 重复执行策略：
--   CREATE TABLE IF NOT EXISTS，可安全重复执行。
--
-- 回滚（确认无新数据依赖后）：
--   DROP TABLE craft_session_event;
--
-- 说明：
--   - context 为脱敏 JSON 快照（标量键值），不存对话或自由文本；
--   - dwell_seconds 独立成列，供「同类步骤停留 P95」查询避免 JSON_EXTRACT
--     的跨库差异（H2 测试库以 TEXT 存 context）；
--   - occurred_at 为事件发生时刻（客户端上报或服务端接收），create_time 为落库时刻。
-- ============================================================

USE zhiyu;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS craft_session_event (
  id               BIGINT       NOT NULL COMMENT '应用侧雪花 ID（ASSIGN_ID）',
  session_id       BIGINT       NOT NULL COMMENT 'craft_session.id',
  user_id          BIGINT       NOT NULL COMMENT '归属用户（服务端按会话填充，不信任客户端）',
  craft_project_id BIGINT       NOT NULL COMMENT '所属项目（服务端按会话填充）',
  event_type       VARCHAR(32)  NOT NULL COMMENT 'step_enter/step_leave/step_advance/step_backtrack/heartbeat/audio_play/visibility_change/app_lifecycle/dwell_stagnation/suspected_away/breathing_prompt/breathing_responded/export/session_finish/session_abandon/session_archive',
  step_no          INT          DEFAULT NULL COMMENT '相关步骤序号（会话级事件为 NULL）',
  occurred_at      DATETIME(3)  NOT NULL COMMENT '事件发生时刻（客户端上报或服务端接收）',
  client_seq       BIGINT       DEFAULT NULL COMMENT '客户端序号（每会话单调），幂等去重；服务端派生事件为 NULL',
  context          JSON         DEFAULT NULL COMMENT '脱敏上下文（标量键值），禁止对话/自由文本',
  dwell_seconds    INT          DEFAULT NULL COMMENT '步骤停留秒数（step_leave 等填写，P95 统计用）',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted          TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_event_seq (session_id, client_seq),
  KEY idx_session_event_session_time (session_id, occurred_at),
  KEY idx_session_event_type_time (event_type, occurred_at),
  KEY idx_session_event_p95 (craft_project_id, event_type, step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作会话行为事件表（2026-11-05 心流度量配套）';
