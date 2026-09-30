-- ============================================================
-- 「织愈」数据库 DDL  MySQL 8.0  InnoDB / utf8mb4
-- 约定：所有业务表含 id / create_time / update_time / deleted（逻辑删除）
--       敏感内容（对话、便签、关怀提醒）应用层加密后存储
-- ============================================================

CREATE DATABASE IF NOT EXISTS zhiyu DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE zhiyu;

-- ---------- 用户 ----------
CREATE TABLE user (
  id          BIGINT       NOT NULL COMMENT '主键',
  username    VARCHAR(50)  NOT NULL COMMENT '登录名',
  password    VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
  nickname    VARCHAR(50)  NOT NULL COMMENT '昵称',
  avatar_url  VARCHAR(255) DEFAULT NULL COMMENT '头像',
  school      VARCHAR(100) DEFAULT NULL COMMENT '所在学校',
  craft_preferences VARCHAR(255) DEFAULT NULL COMMENT '手作偏好标签,逗号分隔',
  can_contact TINYINT      NOT NULL DEFAULT 1 COMMENT '0不允许 1允许心理咨询师联系',
  role        TINYINT      NOT NULL DEFAULT 0 COMMENT '0学生 1传承人 2咨询师 3管理员',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  create_time DATETIME     DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ---------- 情绪会话与消息 ----------
CREATE TABLE emotion_session (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL COMMENT '所属学生',
  mood        VARCHAR(20) DEFAULT NULL COMMENT '触发情绪标签',
  status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0进行中 1已结束',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情绪会话表';

CREATE TABLE emotion_message (
  id          BIGINT      NOT NULL,
  session_id  BIGINT      NOT NULL,
  role        VARCHAR(20) NOT NULL COMMENT 'user/assistant',
  content     TEXT        COMMENT '消息内容(加密)',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';

CREATE TABLE emotion_note (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  content     TEXT        COMMENT '心情便签(加密)',
  mood        VARCHAR(20) DEFAULT NULL,
  note_date   DATE        DEFAULT NULL COMMENT '记录日期',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心情便签表';

-- ---------- 关怀提醒 ----------
CREATE TABLE care_reminder (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  session_id  BIGINT      DEFAULT NULL,
  risk_level  TINYINT     NOT NULL COMMENT '1一般 2高危',
  summary     TEXT        COMMENT '脱敏摘要(加密)',
  status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0待处理 1已跟进',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关怀提醒表';

-- ---------- 手作项目 ----------
CREATE TABLE craft_project (
  id           BIGINT       NOT NULL,
  inheritor_id BIGINT       NOT NULL COMMENT '上传传承人',
  title        VARCHAR(100) NOT NULL,
  intro        TEXT,
  culture_bg   TEXT         COMMENT '文化背景',
  level        TINYINT      NOT NULL DEFAULT 1 COMMENT '1入门 2进阶 3高阶',
  est_minutes  INT          DEFAULT NULL COMMENT '预计时长(分钟)',
  therapy_tags VARCHAR(255) COMMENT '疗愈标签,逗号分隔',
  cover_url    VARCHAR(255),
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审 1上架 2下架',
  create_time  DATETIME     DEFAULT NULL,
  update_time  DATETIME     DEFAULT NULL,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_inheritor (inheritor_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手作项目表';

CREATE TABLE craft_step (
  id         BIGINT       NOT NULL,
  project_id BIGINT       NOT NULL,
  step_no    INT          NOT NULL,
  title      VARCHAR(100),
  guide      TEXT         COMMENT '引导语',
  audio_url  VARCHAR(255) COMMENT '引导语音频',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手作步骤表';

CREATE TABLE craft_material (
  id         BIGINT       NOT NULL,
  project_id BIGINT       NOT NULL,
  name       VARCHAR(100) NOT NULL,
  qty        VARCHAR(50),
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手作材料表';

-- ---------- 推荐 ----------
CREATE TABLE craft_recommendation (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  session_id  BIGINT      DEFAULT NULL,
  mood        VARCHAR(20),
  reason      TEXT        COMMENT '整体理由(加密)',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐主表';

CREATE TABLE craft_recommendation_item (
  id              BIGINT      NOT NULL,
  recommendation_id BIGINT    NOT NULL,
  project_id      BIGINT      NOT NULL,
  score           DOUBLE      DEFAULT NULL,
  reason          VARCHAR(255),
  create_time     DATETIME    DEFAULT NULL,
  update_time     DATETIME    DEFAULT NULL,
  deleted         TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_rec (recommendation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐明细表';

-- ---------- 创作会话 ----------
CREATE TABLE craft_session (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  project_id  BIGINT      NOT NULL,
  status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0待开始 1进行中 2暂停 3完成 4放弃',
  current_step INT        NOT NULL DEFAULT 1 COMMENT '当前步骤序号',
  start_time  DATETIME    DEFAULT NULL,
  end_time    DATETIME    DEFAULT NULL,
  flow_score  INT         DEFAULT 0 COMMENT '心流累计得分/时长(分钟)',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作会话表';

-- ---------- 作品与互动 ----------
CREATE TABLE work (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  project_id  BIGINT       DEFAULT NULL,
  session_id  BIGINT       DEFAULT NULL,
  title       VARCHAR(100),
  cover_url   VARCHAR(255),
  message     TEXT         COMMENT 'AI 寄语',
  visible     TINYINT      NOT NULL DEFAULT 1 COMMENT '0私密 1公开 2仅小组',
  like_count  INT          NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0待审 1已发布 2下架',
  create_time DATETIME     DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品表';

CREATE TABLE work_comment (
  id          BIGINT       NOT NULL,
  work_id     BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  content     VARCHAR(255) NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1展示 0隐藏',
  create_time DATETIME     DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_work (work_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品评论(留暖)表';

CREATE TABLE work_like (
  id          BIGINT   NOT NULL,
  work_id     BIGINT   NOT NULL,
  user_id     BIGINT   NOT NULL,
  type        TINYINT  NOT NULL DEFAULT 1 COMMENT '1点赞 0收藏',
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  deleted     TINYINT  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_work_user (work_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品点赞表';

-- ---------- 互助小组 ----------
CREATE TABLE support_group (
  id          BIGINT       NOT NULL,
  name        VARCHAR(100) NOT NULL,
  description  VARCHAR(255),
  max_count   INT          NOT NULL DEFAULT 50 COMMENT '人数上限',
  member_count INT          NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0关闭 1开放',
  create_time DATETIME     DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='互助小组表';

CREATE TABLE group_member (
  id          BIGINT   NOT NULL,
  group_id    BIGINT   NOT NULL,
  user_id     BIGINT   NOT NULL,
  role        VARCHAR(20) NOT NULL DEFAULT '成员' COMMENT '成员/组长',
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  deleted     TINYINT  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_user (group_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小组成员表';

-- ---------- 传承人 ----------
CREATE TABLE inheritor_profile (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  skill       VARCHAR(100) NOT NULL COMMENT '擅长技艺',
  intro       TEXT,
  honor       VARCHAR(255) COMMENT '荣誉头衔',
  verified    TINYINT      NOT NULL DEFAULT 0,
  create_time DATETIME     DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传承人资料表';

-- ---------- AI 审计日志 ----------
CREATE TABLE ai_audit_log (
  id          BIGINT      NOT NULL,
  scene       VARCHAR(30) NOT NULL COMMENT 'chat/recommend/message',
  raw_input   TEXT        COMMENT '输入(加密)',
  ai_output   TEXT        COMMENT '输出(加密)',
  action      VARCHAR(20) NOT NULL COMMENT 'pass/review/block',
  reason      VARCHAR(255),
  create_time DATETIME    DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_scene (scene)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI审核日志表';
