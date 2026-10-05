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
privacy_agreed    TINYINT      NOT NULL DEFAULT 0,
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
  cover_url    VARCHAR(255),
  status       TINYINT      NOT NULL DEFAULT 0,
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

-- ---------- 作品与互动 ----------
CREATE TABLE IF NOT EXISTS work (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  project_id  BIGINT       DEFAULT NULL,
  session_id  BIGINT       DEFAULT NULL,
  title       VARCHAR(100),
  cover_url   VARCHAR(255),
  message     CLOB,
  visible     TINYINT      NOT NULL DEFAULT 1,
  anonymous   TINYINT      NOT NULL DEFAULT 1,
  like_count  INT          NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1,
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
  scene       VARCHAR(30) NOT NULL,
  raw_input   CLOB,
  ai_output   CLOB,
  action      VARCHAR(20) NOT NULL,
  reason      VARCHAR(255),
  create_time TIMESTAMP   DEFAULT NULL,
  PRIMARY KEY (id)
);

-- ============================================================
-- 索引（与 schema.sql 一一对应，仅名称加表名前缀以规避 H2 的 schema 级命名空间）
-- ============================================================
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
CREATE INDEX IF NOT EXISTS idx_craft_session_user            ON craft_session (user_id);
CREATE INDEX IF NOT EXISTS idx_craft_session_project         ON craft_session (project_id);
CREATE INDEX IF NOT EXISTS idx_work_user                     ON work (user_id);
CREATE INDEX IF NOT EXISTS idx_work_status                   ON work (status);
CREATE INDEX IF NOT EXISTS idx_work_comment_work             ON work_comment (work_id);
CREATE INDEX IF NOT EXISTS idx_ai_audit_log_scene            ON ai_audit_log (scene);
