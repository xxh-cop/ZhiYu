-- P1b 对话上下文滚动摘要（2026-11-04，rolling-summary 默认关闭）。
-- 幂等：表不存在才创建，重复执行安全。
USE zhiyu;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS dialogue_context_summary (
  id                 BIGINT       NOT NULL,
  user_id            BIGINT       NOT NULL COMMENT '归属用户（本人数据隔离）',
  session_id         BIGINT       NOT NULL COMMENT '情绪会话 id',
  covered_start_turn INT          NOT NULL COMMENT '摘要覆盖的起始轮次（按消息序，从 1 计）',
  covered_end_turn   INT          NOT NULL COMMENT '摘要覆盖的截止轮次（含）',
  summary_cipher     TEXT         NOT NULL COMMENT '摘要密文（应用层加密，AesEncryptor）',
  model              VARCHAR(80)  DEFAULT NULL COMMENT '生成模型（standard 档）',
  prompt_version     VARCHAR(40)  DEFAULT NULL COMMENT 'prompt 模板版本（context-summary.st）',
  create_time        DATETIME     DEFAULT NULL,
  update_time        DATETIME     DEFAULT NULL,
  deleted            TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_dcs_user_session (user_id, session_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话上下文滚动摘要（P1b，rolling-summary 默认关闭）';
