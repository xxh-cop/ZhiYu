-- AJ 危机路径留存（2026-11-04）：只存脱敏摘要与危机等级，绝不存原文。
-- 幂等：表不存在才创建，重复执行安全。
USE zhiyu;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS crisis_session_log (
  id             BIGINT       NOT NULL COMMENT '雪花 id（应用侧 ASSIGN_ID，不设 AUTO_INCREMENT）',
  user_id        BIGINT       NOT NULL COMMENT '触发用户',
  session_id     BIGINT       DEFAULT NULL COMMENT '关联会话/来源标识（情绪会话或练习会话，可空）',
  scene          VARCHAR(30)  NOT NULL COMMENT '触发场景（chat/companion/asr 等）',
  risk_level     TINYINT      NOT NULL DEFAULT 2 COMMENT '危机等级（与 care_reminder 口径一致）',
  summary_cipher TEXT         NOT NULL COMMENT '脱敏摘要密文（应用层 AES；规则式概括，绝不含用户/AI 原文）',
  create_time    DATETIME     NOT NULL,
  update_time    DATETIME     NOT NULL,
  deleted        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_crisis_user (user_id, deleted),
  KEY idx_crisis_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='危机路径留存（降级：只存脱敏摘要，不存原文；仅授权关怀角色可见）';
