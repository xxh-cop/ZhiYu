-- L4 Companion：统一练习会话、授权偏好记忆、AI 运行元数据。
-- 只新增表，不修改现有业务表；可重复执行。
CREATE TABLE IF NOT EXISTS practice_session (
  id                    BIGINT      NOT NULL,
  user_id               BIGINT      NOT NULL,
  kind                  VARCHAR(20) NOT NULL COMMENT 'HERITAGE/CRAFT/MINDFULNESS/DRAWING',
  status                TINYINT     NOT NULL DEFAULT 0 COMMENT '0 CREATED 1 LISTENING 2 RECOMMENDED 3 PRACTICING 4 PAUSED 5 COMPLETED 6 ARTIFACT_SAVED 7 ABANDONED',
  mood_code             VARCHAR(32) DEFAULT NULL,
  minutes_budget        INT         NOT NULL,
  emotion_session_id    BIGINT      DEFAULT NULL,
  craft_session_id      BIGINT      DEFAULT NULL,
  heritage_pattern_id   VARCHAR(32) DEFAULT NULL,
  current_step          INT         NOT NULL DEFAULT 1,
  context_cipher        TEXT        DEFAULT NULL,
  started_at            DATETIME    DEFAULT NULL,
  completed_at          DATETIME    DEFAULT NULL,
  create_time           DATETIME    DEFAULT NULL,
  update_time           DATETIME    DEFAULT NULL,
  deleted               TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_practice_user_status (user_id,status),
  KEY idx_practice_user_updated (user_id,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一练习会话表';

CREATE TABLE IF NOT EXISTS user_ai_memory (
  id              BIGINT      NOT NULL,
  user_id         BIGINT      NOT NULL,
  memory_key      VARCHAR(40) NOT NULL,
  value_cipher    TEXT        NOT NULL,
  consent_version VARCHAR(20) NOT NULL,
  source          VARCHAR(20) NOT NULL,
  expires_at      DATETIME    DEFAULT NULL,
  create_time     DATETIME    DEFAULT NULL,
  update_time     DATETIME    DEFAULT NULL,
  deleted         TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_ai_memory (user_id,memory_key),
  KEY idx_ai_memory_user (user_id,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户授权的陪伴偏好记忆';

CREATE TABLE IF NOT EXISTS ai_trace (
  id                  BIGINT      NOT NULL,
  trace_id            VARCHAR(64) NOT NULL,
  user_id             BIGINT      DEFAULT NULL,
  practice_session_id BIGINT      DEFAULT NULL,
  scene               VARCHAR(30) NOT NULL,
  model               VARCHAR(80) DEFAULT NULL,
  prompt_version      VARCHAR(40) DEFAULT NULL,
  tool_name           VARCHAR(60) DEFAULT NULL,
  latency_ms          INT         DEFAULT NULL,
  input_tokens        INT         DEFAULT NULL,
  output_tokens       INT         DEFAULT NULL,
  result_status       VARCHAR(20) NOT NULL,
  fallback_reason     VARCHAR(120) DEFAULT NULL,
  create_time         DATETIME    DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_ai_trace_trace (trace_id),
  KEY idx_ai_trace_session (practice_session_id,create_time),
  KEY idx_ai_trace_scene (scene,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI运行元数据（脱敏）';
