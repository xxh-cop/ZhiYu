-- ============================================================
-- 20261105_craft_session_gate_event.sql
-- 目的：craft_session_event.event_type 注释白名单补 step_gate_blocked
--   （服务端派生：心跳/推进被下架闸口拒绝的证据事件，每会话至多一条；
--   看板只读起点的精确档，session_archive 退为兜底）。
--
-- 说明：
--   - 纯注释变更（VARCHAR(32) 无需扩宽），不改任何既有行；
--   - 幂等：重复执行只是把注释改写成同一份，安全；
--   - H2 测试库该列本就不带注释，无需同步；
--   - SchemaCompatibilityCheck 只核对列存在性，本次无结构变化。
-- ============================================================

USE zhiyu;
SET NAMES utf8mb4;

ALTER TABLE `craft_session_event`
  MODIFY COLUMN `event_type` VARCHAR(32) NOT NULL
  COMMENT 'step_enter/step_leave/step_advance/step_backtrack/heartbeat/audio_play/visibility_change/app_lifecycle/dwell_stagnation/suspected_away/breathing_prompt/breathing_responded/export/session_finish/session_abandon/session_archive/step_gate_blocked（服务端派生，心跳被下架闸口拒绝，每会话至多一条）';
