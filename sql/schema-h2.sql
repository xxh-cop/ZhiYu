-- ============================================================
-- 「织愈」数据库 DDL —— H2 版本（仅供单元测试使用）
--
-- 生产 / 集成测试一律使用 sql/schema.sql（MySQL 8.0）。
-- 本文件的唯一用途：T0.2 单元测试用 H2 + MySQL 兼容模式建库，
-- 使 MyBatis-Plus 的实体映射、字段名、类型、约束与生产保持一致。
--
-- 与 schema.sql 的差异（仅方言与命名空间，语义完全对应）：
--   1) 去掉 MySQL 专有语法：SET NAMES、CREATE DATABASE、USE、
--      ENGINE=InnoDB、DEFAULT CHARSET、COLLATE、列/表 COMMENT
--   2) 索引与唯一约束改名：H2 的索引名、约束名是 schema 级唯一，
--      而 MySQL 是表级唯一（schema.sql 中 idx_user 等重名是合法的）。
--      为不冲突，这里统一加表名前缀，列组合与唯一性语义不变。
--      例：idx_user(emotion_session) -> idx_emotion_session_user
--          uk_code(dict_mood)        -> uk_dict_mood_code
--   3) 表名 / 字段名 / 类型 / NOT NULL / DEFAULT 与 schema.sql 完全一致，
--      异常项仅一处：TEXT -> CLOB（H2 无 TEXT 类型，见下）
--   4) 保留 IF NOT EXISTS，可重复执行
--
-- 建议连接串（H2 2.x）：
--   jdbc:h2:mem:zhiyu;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER
--   MODE=MySQL 提供 MySQL 兼容语义，DATABASE_TO_LOWER 使表名/字段名小写，
--   与 MySQL 的 information_schema 行为接近，避免大小写导致的映射问题。
--   NON_KEYWORDS=USER 见下方说明，务必加上。
-- ============================================================

-- H2 2.x 把 USER 列为保留字（MySQL 不是），不放开则 `CREATE TABLE user (...)`、
-- 以及 MyBatis-Plus 生成的 `SELECT ... FROM user` 都会报
-- "Syntax error ... expected identifier"。
-- 本语句放开该保留字，使其可继续作为不加引号的标识符使用。
-- 注意：这是连接级设置。若测试用连接池，请在 JDBC URL 上同时加 NON_KEYWORDS=USER，
-- 以免后续查询拿到没执行过本语句的连接。
SET NON_KEYWORDS USER;

-- ---------- 字典：情绪 mood / 疗愈效果 therapy_tags / 技艺品类 category ----------
CREATE TABLE IF NOT EXISTS dict_mood (
  id           BIGINT       NOT NULL,
  code         VARCHAR(32)  NOT NULL,
  label        VARCHAR(32)  NOT NULL,
  therapy_tags VARCHAR(255) DEFAULT NULL,
  sort_no      INT          NOT NULL DEFAULT 0,
  create_time  TIMESTAMP    DEFAULT NULL,
  update_time  TIMESTAMP    DEFAULT NULL,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_dict_mood_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS dict_category (
  id          BIGINT       NOT NULL,
  code        VARCHAR(32)  NOT NULL,
  label       VARCHAR(32)  NOT NULL,
  sort_no     INT          NOT NULL DEFAULT 0,
  create_time TIMESTAMP    DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_dict_category_code UNIQUE (code),
  CONSTRAINT uk_dict_category_label UNIQUE (label)
);

CREATE TABLE IF NOT EXISTS dict_therapy_tags (
  id          BIGINT       NOT NULL,
  label       VARCHAR(32)  NOT NULL,
  sort_no     INT          NOT NULL DEFAULT 0,
  create_time TIMESTAMP    DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_dict_therapy_tags_label UNIQUE (label)
);

-- ---------- 用户 ----------
CREATE TABLE IF NOT EXISTS user (
  id                BIGINT       NOT NULL,
  username          VARCHAR(50)  NOT NULL,
  password          VARCHAR(255) NOT NULL,
  nickname          VARCHAR(50)  NOT NULL,
  avatar_url        VARCHAR(255) DEFAULT NULL,
  school            VARCHAR(100) DEFAULT NULL,
  craft_preferences VARCHAR(255) DEFAULT NULL,
can_contact       TINYINT      NOT NULL DEFAULT 0,
privacy_agreed    TINYINT      NOT NULL DEFAULT 0,  baseline_nudge_enabled TINYINT NOT NULL DEFAULT 1,
  baseline_nudge_last_at DATETIME DEFAULT NULL,
  baseline_nudge_last_level VARCHAR(16) DEFAULT NULL,
  stuck_guide_enabled TINYINT NOT NULL DEFAULT 1,

role              TINYINT      NOT NULL DEFAULT 0,
  status            TINYINT      NOT NULL DEFAULT 1,
  create_time       TIMESTAMP    DEFAULT NULL,
  update_time       TIMESTAMP    DEFAULT NULL,
  deleted           TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_user_username UNIQUE (username)
);

-- ---------- 情绪会话与消息 ----------
CREATE TABLE IF NOT EXISTS emotion_session (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  mood        VARCHAR(32) DEFAULT NULL,
  status      TINYINT     NOT NULL DEFAULT 0,
  create_time TIMESTAMP   DEFAULT NULL,
  update_time TIMESTAMP   DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS emotion_message (
  id          BIGINT      NOT NULL,
  session_id  BIGINT      NOT NULL,
  role        VARCHAR(20) NOT NULL,
  content     CLOB,
  create_time TIMESTAMP   DEFAULT NULL,
  update_time TIMESTAMP   DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS emotion_note (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  content     CLOB,
  mood        VARCHAR(32) DEFAULT NULL,
  note_date   DATE        DEFAULT NULL,
  create_time TIMESTAMP   DEFAULT NULL,
  update_time TIMESTAMP   DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

-- ---------- 关怀提醒 ----------
CREATE TABLE IF NOT EXISTS care_reminder (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  session_id  BIGINT      DEFAULT NULL,
  risk_level  TINYINT     NOT NULL,
  summary     CLOB,
  status      TINYINT     NOT NULL DEFAULT 0,
  create_time TIMESTAMP   DEFAULT NULL,
  update_time TIMESTAMP   DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

-- ---------- 手作项目 ----------
CREATE TABLE IF NOT EXISTS craft_project (
  id           BIGINT       NOT NULL,
  inheritor_id BIGINT       NOT NULL,
  title        VARCHAR(100) NOT NULL,
  intro        CLOB,
  culture_bg   CLOB,
  level        TINYINT      NOT NULL DEFAULT 1,
  est_minutes  INT          DEFAULT NULL,
  category     VARCHAR(32)  NOT NULL DEFAULT '',
  therapy_tags VARCHAR(255),
    video_url VARCHAR(255) DEFAULT NULL,
  cover_url    VARCHAR(255),
  status       TINYINT      NOT NULL DEFAULT 0,
  audit_reason VARCHAR(300)  DEFAULT NULL,
  takedown_kind VARCHAR(16)  DEFAULT NULL,
  takedown_at  TIMESTAMP    DEFAULT NULL,
  archive_at   TIMESTAMP    DEFAULT NULL,
  export_until TIMESTAMP    DEFAULT NULL,
  notice_key   VARCHAR(64)   DEFAULT NULL,
  create_time  TIMESTAMP    DEFAULT NULL,
  update_time  TIMESTAMP    DEFAULT NULL,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS craft_step (
  id          BIGINT       NOT NULL,
  project_id  BIGINT       NOT NULL,
  step_no     INT          NOT NULL,
  title       VARCHAR(100),
  guide       CLOB,
  audio_url   VARCHAR(255),
  license_status VARCHAR(16) NOT NULL DEFAULT 'none',
  attribution  VARCHAR(300)  DEFAULT NULL,
  asset_status VARCHAR(16)   DEFAULT NULL,
  create_time TIMESTAMP    DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS craft_material (
  id          BIGINT       NOT NULL,
  project_id  BIGINT       NOT NULL,
  name        VARCHAR(100) NOT NULL,
  qty         VARCHAR(50),
  create_time TIMESTAMP    DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

-- ---------- 推荐 ----------
CREATE TABLE IF NOT EXISTS craft_recommendation (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  session_id  BIGINT      DEFAULT NULL,
  mood        VARCHAR(32),
  reason      CLOB,
  create_time TIMESTAMP   DEFAULT NULL,
  update_time TIMESTAMP   DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS craft_recommendation_item (
  id                BIGINT      NOT NULL,
  recommendation_id BIGINT      NOT NULL,
  project_id        BIGINT      NOT NULL,
  score             DOUBLE      DEFAULT NULL,
  reason            VARCHAR(255),
  create_time       TIMESTAMP   DEFAULT NULL,
  update_time       TIMESTAMP   DEFAULT NULL,
  deleted           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS craft_recommend_event (
  id                BIGINT      NOT NULL,
  user_id           BIGINT      NOT NULL,
  recommendation_id BIGINT      NOT NULL,
  project_id        BIGINT      NOT NULL,
  event_type        VARCHAR(16) NOT NULL DEFAULT 'CLICK',
  source            VARCHAR(16) NOT NULL,
  create_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

-- ---------- 创作会话 ----------
CREATE TABLE IF NOT EXISTS craft_session (
  id           BIGINT      NOT NULL,
  user_id      BIGINT      NOT NULL,
  project_id   BIGINT      NOT NULL,
  status       TINYINT     NOT NULL DEFAULT 0,
  current_step INT         NOT NULL DEFAULT 1,
  start_time   TIMESTAMP   DEFAULT NULL,
  end_time     TIMESTAMP   DEFAULT NULL,
  flow_seconds INT         DEFAULT 0,
  create_time  TIMESTAMP   DEFAULT NULL,
  update_time  TIMESTAMP   DEFAULT NULL,
  deleted      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

-- ---------- 创作会话行为事件（2026-11-05 心流度量配套；context 在 H2 以 TEXT 存 JSON） ----------
CREATE TABLE IF NOT EXISTS craft_session_event (
  id               BIGINT       NOT NULL,
  session_id       BIGINT       NOT NULL,
  user_id          BIGINT       NOT NULL,
  craft_project_id BIGINT       NOT NULL,
  event_type       VARCHAR(32)  NOT NULL,
  step_no          INT          DEFAULT NULL,
  occurred_at      TIMESTAMP(3) NOT NULL,
  client_seq       BIGINT       DEFAULT NULL,
  context          TEXT         DEFAULT NULL,
  dwell_seconds    INT          DEFAULT NULL,
  create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted          TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

-- ---------- 作品与互动 ----------
CREATE TABLE IF NOT EXISTS work (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  project_id  BIGINT       DEFAULT NULL,
  session_id  BIGINT       DEFAULT NULL,
  title       VARCHAR(100),
  cover_url   VARCHAR(255),
  media_type VARCHAR(10) NOT NULL DEFAULT 'image',
  video_url VARCHAR(255) DEFAULT NULL,
  message     CLOB,
  visible     TINYINT      NOT NULL DEFAULT 1,
  anonymous   TINYINT      NOT NULL DEFAULT 1,
  like_count  INT          NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1,
  audit_reason VARCHAR(255) DEFAULT NULL,
  soft_demote_until TIMESTAMP DEFAULT NULL,
  create_time TIMESTAMP    DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS work_comment (
  id          BIGINT       NOT NULL,
  work_id     BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  content     VARCHAR(255) NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 1,
  audit_reason VARCHAR(255) DEFAULT NULL,
  soft_demote_until TIMESTAMP DEFAULT NULL,
  create_time TIMESTAMP    DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS work_like (
  id          BIGINT    NOT NULL,
  work_id     BIGINT    NOT NULL,
  user_id     BIGINT    NOT NULL,
  type        TINYINT   NOT NULL DEFAULT 1,
  create_time TIMESTAMP DEFAULT NULL,
  update_time TIMESTAMP DEFAULT NULL,
  deleted     TINYINT   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_work_like_work_user_type UNIQUE (work_id, user_id, type)
);

-- ---------- 互助小组 ----------
CREATE TABLE IF NOT EXISTS support_group (
  id           BIGINT       NOT NULL,
  name         VARCHAR(100) NOT NULL,
  description  VARCHAR(255),
  max_count    INT          NOT NULL DEFAULT 50,
  member_count INT          NOT NULL DEFAULT 0,
  status       TINYINT      NOT NULL DEFAULT 1,
  create_time  TIMESTAMP    DEFAULT NULL,
  update_time  TIMESTAMP    DEFAULT NULL,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS group_member (
  id          BIGINT      NOT NULL,
  group_id    BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  role        VARCHAR(20) NOT NULL DEFAULT '成员',
  create_time TIMESTAMP   DEFAULT NULL,
  update_time TIMESTAMP   DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_group_member_group_user UNIQUE (group_id, user_id)
);

-- ---------- 传承人 ----------
CREATE TABLE IF NOT EXISTS inheritor_profile (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  skill       VARCHAR(100) NOT NULL,
  intro       CLOB,
  honor       VARCHAR(255),
  verified    TINYINT      NOT NULL DEFAULT 0,
  create_time TIMESTAMP    DEFAULT NULL,
  update_time TIMESTAMP    DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_inheritor_profile_user UNIQUE (user_id)
);

-- ---------- AI 审计日志（只追加，故只保留 create_time、无 deleted） ----------
CREATE TABLE IF NOT EXISTS ai_audit_log (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      DEFAULT NULL,
  scene       VARCHAR(30) NOT NULL,
  raw_input   CLOB,
  ai_output   CLOB,
  action      VARCHAR(20) NOT NULL,
  reason      VARCHAR(255),
  ref_type    VARCHAR(30) DEFAULT NULL,
  ref_id      BIGINT      DEFAULT NULL,
  create_time TIMESTAMP   DEFAULT NULL,
  PRIMARY KEY (id)
);

-- ============================================================
-- 索引（与 schema.sql 一一对应，仅名称加表名前缀以规避 H2 的 schema 级命名空间）
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_audit_ref                         ON ai_audit_log (ref_type,ref_id);
CREATE INDEX IF NOT EXISTS idx_emotion_session_user          ON emotion_session (user_id);
CREATE INDEX IF NOT EXISTS idx_emotion_message_session       ON emotion_message (session_id);
CREATE INDEX IF NOT EXISTS idx_emotion_note_user             ON emotion_note (user_id);
CREATE INDEX IF NOT EXISTS idx_care_reminder_user            ON care_reminder (user_id);
CREATE INDEX IF NOT EXISTS idx_care_reminder_status          ON care_reminder (status);
CREATE INDEX IF NOT EXISTS idx_craft_project_inheritor       ON craft_project (inheritor_id);
CREATE INDEX IF NOT EXISTS idx_craft_project_status          ON craft_project (status);
CREATE INDEX IF NOT EXISTS idx_craft_step_project            ON craft_step (project_id);
CREATE INDEX IF NOT EXISTS idx_craft_material_project        ON craft_material (project_id);
CREATE INDEX IF NOT EXISTS idx_craft_recommendation_user     ON craft_recommendation (user_id);
CREATE INDEX IF NOT EXISTS idx_craft_recommendation_item_rec ON craft_recommendation_item (recommendation_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_craft_recommend_event   ON craft_recommend_event (user_id, recommendation_id, project_id, event_type);
CREATE INDEX IF NOT EXISTS idx_craft_recommend_event_time    ON craft_recommend_event (event_type, create_time);
CREATE INDEX IF NOT EXISTS idx_craft_recommend_event_rec     ON craft_recommend_event (recommendation_id);
CREATE INDEX IF NOT EXISTS idx_craft_session_user            ON craft_session (user_id);
CREATE INDEX IF NOT EXISTS idx_craft_session_project         ON craft_session (project_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_craft_session_event_seq          ON craft_session_event (session_id, client_seq);
CREATE INDEX IF NOT EXISTS idx_craft_session_event_session_time       ON craft_session_event (session_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_craft_session_event_type_time          ON craft_session_event (event_type, occurred_at);
CREATE INDEX IF NOT EXISTS idx_craft_session_event_p95                ON craft_session_event (craft_project_id, event_type, step_no);
CREATE INDEX IF NOT EXISTS idx_work_user                     ON work (user_id);
CREATE INDEX IF NOT EXISTS idx_work_status                   ON work (status);
CREATE INDEX IF NOT EXISTS idx_work_comment_work             ON work_comment (work_id);
CREATE INDEX IF NOT EXISTS idx_ai_audit_log_scene            ON ai_audit_log (scene);
CREATE INDEX IF NOT EXISTS idx_ai_audit_log_user             ON ai_audit_log (user_id);

-- Additive migration; no existing rows are modified.


-- Growth modules: encrypted personal records, scheduled programs and capacity-safe bookings.
CREATE TABLE IF NOT EXISTS wellbeing_record (
 id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, kind VARCHAR(24) NOT NULL,
 record_key VARCHAR(100) NOT NULL, payload TEXT NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_wellbeing_record (user_id, kind, record_key),
 KEY idx_wellbeing_owner (user_id, create_time)
);
CREATE TABLE IF NOT EXISTS learning_program (
 id BIGINT PRIMARY KEY, owner_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL,
 title VARCHAR(100) NOT NULL, description TEXT NOT NULL, location VARCHAR(200) NOT NULL,
 co_leader VARCHAR(100) NOT NULL, materials VARCHAR(1000) NOT NULL,
 start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
 capacity INT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 KEY idx_program_schedule (status,start_time)
);
CREATE TABLE IF NOT EXISTS program_booking (
 id BIGINT PRIMARY KEY, program_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'BOOKED',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_program_member (program_id,user_id), KEY idx_booking_owner (user_id)
);
CREATE TABLE IF NOT EXISTS craft_safety (
 id BIGINT PRIMARY KEY, project_id BIGINT NOT NULL UNIQUE, medium VARCHAR(20) NOT NULL,
 level VARCHAR(16) NOT NULL, hazards VARCHAR(300) NOT NULL, guidance VARCHAR(1000) NOT NULL,
 source VARCHAR(300) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS media_asset (
 id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, storage_key VARCHAR(180) NOT NULL, size_bytes BIGINT NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL, deleted TINYINT NOT NULL DEFAULT 0
);

-- Final business workflows: invitations never expose personal emotional records.
CREATE TABLE IF NOT EXISTS program_collaborator (
 id BIGINT PRIMARY KEY, program_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_program_collaborator (program_id,user_id)
);
CREATE TABLE IF NOT EXISTS guidance_channel (
 id BIGINT PRIMARY KEY, booking_id BIGINT NOT NULL UNIQUE, enabled TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS guidance_message (
 id BIGINT PRIMARY KEY, booking_id BIGINT NOT NULL, user_id BIGINT NOT NULL, content TEXT NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 KEY idx_guidance_history (booking_id,id)
);
CREATE TABLE IF NOT EXISTS group_companion (
 id BIGINT PRIMARY KEY, group_id BIGINT NOT NULL, first_user_id BIGINT NOT NULL,
 second_user_id BIGINT NOT NULL, requester_id BIGINT NOT NULL, state VARCHAR(16) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_companion_pair (group_id,first_user_id,second_user_id)
);
CREATE TABLE IF NOT EXISTS group_work (
 id BIGINT PRIMARY KEY, group_id BIGINT NOT NULL, work_id BIGINT NOT NULL UNIQUE,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 KEY idx_group_work (group_id,work_id)
);
CREATE TABLE IF NOT EXISTS runtime_config (
 id BIGINT PRIMARY KEY, config_key VARCHAR(80) NOT NULL UNIQUE, payload TEXT NOT NULL, revision INT NOT NULL DEFAULT 1,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS file_cleanup_job (
 id BIGINT PRIMARY KEY, storage_kind VARCHAR(16) NOT NULL, storage_key VARCHAR(255) NOT NULL,
 attempts INT NOT NULL DEFAULT 0, state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_cleanup_key (storage_kind,storage_key)
);

CREATE TABLE IF NOT EXISTS notification (
 id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, type VARCHAR(30) NOT NULL, title VARCHAR(120) NOT NULL,
 content VARCHAR(500) DEFAULT NULL, ref_type VARCHAR(30) DEFAULT NULL, ref_id BIGINT DEFAULT NULL, sub_ref_id BIGINT DEFAULT NULL,
 dedupe_key VARCHAR(80) NOT NULL, read_flag TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_notification_dedupe (user_id,type,ref_type,ref_id,dedupe_key)
);
CREATE INDEX IF NOT EXISTS idx_notification_user_read ON notification(user_id,read_flag,id);
CREATE INDEX IF NOT EXISTS idx_notification_user_type ON notification(user_id,type,id);

CREATE TABLE IF NOT EXISTS admin_export_log (
 id BIGINT PRIMARY KEY, actor_id BIGINT NOT NULL, actor_role TINYINT NOT NULL,
 target_type VARCHAR(20) NOT NULL, filter_summary VARCHAR(500) DEFAULT NULL,
 field_list VARCHAR(500) NOT NULL, record_count INT NOT NULL DEFAULT 0,
 purpose VARCHAR(200) NOT NULL, batch_no VARCHAR(32) NOT NULL,
 alert_flag TINYINT NOT NULL DEFAULT 0,
 create_time TIMESTAMP DEFAULT NULL,
 UNIQUE KEY uk_export_batch (batch_no)
);

CREATE TABLE IF NOT EXISTS dialogue_context_summary (
 id BIGINT NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL,
 session_id BIGINT NOT NULL,
 covered_start_turn INT NOT NULL,
 covered_end_turn INT NOT NULL,
 summary_cipher TEXT NOT NULL,
 model VARCHAR(80) DEFAULT NULL,
 prompt_version VARCHAR(40) DEFAULT NULL,
 create_time DATETIME DEFAULT NULL,
 update_time DATETIME DEFAULT NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 KEY idx_dcs_user_session (user_id, session_id, deleted)
);

-- ---------- 危机路径留存（AJ 2026-11-04：只存脱敏摘要与危机等级，绝不存原文） ----------
CREATE TABLE IF NOT EXISTS crisis_session_log (
  id             BIGINT       NOT NULL,
  user_id        BIGINT       NOT NULL,
  session_id     BIGINT       DEFAULT NULL,
  scene          VARCHAR(30)  NOT NULL,
  risk_level     TINYINT      NOT NULL DEFAULT 2,
  summary_cipher TEXT         NOT NULL,
  create_time    DATETIME     NOT NULL,
  update_time    DATETIME     NOT NULL,
  deleted        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_crisis_user (user_id, deleted),
  KEY idx_crisis_created (create_time)
);

-- ---------- 审核软档处置链（2026-11-07：软档台账 + 抽检池一体） ----------
CREATE TABLE IF NOT EXISTS moderation_soft_review (
  id            BIGINT       NOT NULL,
  target_type   VARCHAR(10)  NOT NULL,
  target_id     BIGINT       NOT NULL,
  author_id     BIGINT       NOT NULL,
  ai_tendency   VARCHAR(20)  NOT NULL DEFAULT 'SOFT_CONCERN',
  narrative     VARCHAR(20)  DEFAULT NULL,
  audit_log_id  BIGINT       DEFAULT NULL,
  sampled       TINYINT      NOT NULL DEFAULT 0,
  sample_layer  VARCHAR(30)  DEFAULT NULL,
  pool_status   VARCHAR(20)  NOT NULL DEFAULT 'NOT_SAMPLED',
  reviewer_id   BIGINT       DEFAULT NULL,
  reason        VARCHAR(255) DEFAULT NULL,
  sla_due_at    TIMESTAMP    DEFAULT NULL,
  reviewed_at   TIMESTAMP    DEFAULT NULL,
  demote_until  TIMESTAMP    DEFAULT NULL,
  appeal_status VARCHAR(20)  DEFAULT NULL,
  appeal_reason VARCHAR(200) DEFAULT NULL,
  notified      TINYINT      NOT NULL DEFAULT 0,
  create_time   TIMESTAMP    DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_soft_target UNIQUE (target_type, target_id),
  KEY idx_pool_sla (pool_status, sla_due_at),
  KEY idx_soft_author (author_id)
);

-- 软档影子模式观测台账（2026-11-07：只观测不处置；shadow.enabled 默认 false）
CREATE TABLE IF NOT EXISTS moderation_soft_shadow (
  id            BIGINT       NOT NULL,
  target_type   VARCHAR(10)  NOT NULL,
  target_id     BIGINT       NOT NULL,
  author_id     BIGINT       NOT NULL,
  judge_status  VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
  tendency      VARCHAR(20)  DEFAULT NULL,
  narrative     VARCHAR(20)  DEFAULT NULL,
  audit_log_id  BIGINT       DEFAULT NULL,
  provider      VARCHAR(30)  NOT NULL DEFAULT 'unknown',
  model         VARCHAR(80)  NOT NULL DEFAULT 'unknown',
  attempts      INT          NOT NULL DEFAULT 0,
  last_attempt_at TIMESTAMP  DEFAULT NULL,
  target_create_time TIMESTAMP DEFAULT NULL,
  create_time   TIMESTAMP    DEFAULT NULL,
  update_time   TIMESTAMP    DEFAULT NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_shadow_target UNIQUE (target_type, target_id),
  KEY idx_shadow_target_ct (target_create_time),
  KEY idx_shadow_retry (judge_status, attempts)
);
