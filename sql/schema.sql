-- ============================================================
-- 「织愈」数据库 DDL  MySQL 8.0  InnoDB / utf8mb4
-- 约定：
--   1) 所有业务表含 id / create_time / update_time / deleted（逻辑删除）
--   2) 敏感内容（对话、便签、推荐理由）应用层加密后存储；
--      care_reminder.summary 为脱敏文本，不加密
--   3) 主键由应用侧雪花算法生成（MyBatis-Plus ASSIGN_ID），DDL 不设 AUTO_INCREMENT；
--      种子数据可手动指定小整数 ID（见 sql/init-data.sql），与雪花 ID 不会冲突
--   4) 本文件幂等：全部 CREATE TABLE IF NOT EXISTS，可重复执行不报错（不 DROP、不删数据）；
--      需要彻底重置本地库时使用 sql/reset.sql
-- ============================================================

-- 强制连接字符集为 utf8mb4。
-- Windows 版 mysql 客户端默认 character_set_client=gbk，本文件为 UTF-8 编码，
-- 若不显式声明，中文会被按 GBK 解析成乱码，并可能触发
-- "ERROR 1406 Data too long for column ..." —— 故必须在任何中文语句之前执行。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS zhiyu DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE zhiyu;

-- ---------- 字典：情绪 mood / 疗愈效果 therapy_tags / 技艺品类 category ----------
-- 三套标签严格分开，不可混用：
--   1) mood         情绪分类，英文 code：anxious/stressed/depressed/angry/tired
--   2) therapy_tags 疗愈效果标签，中文规范词表，词表见 dict_therapy_tags
--                   用于推荐匹配（mood -> dict_mood.therapy_tags -> craft_project.therapy_tags）
--                   不含技艺名、不含情绪词
--   3) category     技艺品类（赣绣/剪纸/扎染/陶艺/编织/篆刻/捶草印花），仅前端展示，不参与匹配
CREATE TABLE IF NOT EXISTS dict_mood (
  id           BIGINT       NOT NULL,
  code         VARCHAR(32)  NOT NULL COMMENT '英文 code：anxious/stressed/depressed/angry/tired',
  label        VARCHAR(32)  NOT NULL COMMENT '中文展示名',
  therapy_tags VARCHAR(255) DEFAULT NULL COMMENT '该情绪优先推荐的疗愈效果标签,逗号分隔,词表见 dict_therapy_tags',
  sort_no      INT          NOT NULL DEFAULT 0 COMMENT '展示排序',
  create_time  DATETIME     DEFAULT NULL,
  update_time  DATETIME     DEFAULT NULL,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情绪字典表';

-- 技艺品类字典（仅展示用；craft_project.category 存 label）
CREATE TABLE IF NOT EXISTS dict_category (
  id          BIGINT       NOT NULL,
  code        VARCHAR(32)  NOT NULL COMMENT '英文/拼音 code，如 zharan',
  label       VARCHAR(32)  NOT NULL COMMENT '中文品类名，如 扎染',
  sort_no     INT          NOT NULL DEFAULT 0 COMMENT '展示排序',
  create_time DATETIME     DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  UNIQUE KEY uk_label (label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技艺品类字典表';

-- 疗愈效果标签字典（craft_project.therapy_tags / dict_mood.therapy_tags 的规范词表）
CREATE TABLE IF NOT EXISTS dict_therapy_tags (
  id          BIGINT       NOT NULL,
  label       VARCHAR(32)  NOT NULL COMMENT '中文规范词，如 专注',
  sort_no     INT          NOT NULL DEFAULT 0 COMMENT '展示排序',
  create_time DATETIME     DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_label (label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='疗愈效果标签字典表';

-- ---------- 用户 ----------
CREATE TABLE IF NOT EXISTS user (
  id          BIGINT       NOT NULL COMMENT '主键',
  username    VARCHAR(50)  NOT NULL COMMENT '登录名（学号或昵称）',
  password    VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
  nickname    VARCHAR(50)  NOT NULL COMMENT '昵称',
  avatar_url  VARCHAR(255) DEFAULT NULL COMMENT '头像',
  school      VARCHAR(100) DEFAULT NULL COMMENT '所在学校',
  craft_preferences VARCHAR(255) DEFAULT NULL COMMENT '手作偏好标签,逗号分隔',
  can_contact TINYINT      NOT NULL DEFAULT 0 COMMENT '0不允许 1允许心理咨询师联系；默认 0，需用户单独授权',
  role        TINYINT      NOT NULL DEFAULT 0 COMMENT '0学生 1传承人 2咨询师 3管理员',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  create_time DATETIME     DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ---------- 情绪会话与消息 ----------
CREATE TABLE IF NOT EXISTS emotion_session (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL COMMENT '所属学生',
  mood        VARCHAR(32) DEFAULT NULL COMMENT '触发情绪 code，取值见 dict_mood.code',
  status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0进行中 1已结束',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情绪会话表';

CREATE TABLE IF NOT EXISTS emotion_message (
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

CREATE TABLE IF NOT EXISTS emotion_note (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  content     TEXT        COMMENT '心情便签(加密)',
  mood        VARCHAR(32) DEFAULT NULL COMMENT '情绪 code，取值见 dict_mood.code',
  note_date   DATE        DEFAULT NULL COMMENT '记录日期',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='心情便签表';

-- ---------- 关怀提醒 ----------
CREATE TABLE IF NOT EXISTS care_reminder (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  session_id  BIGINT      DEFAULT NULL,
  risk_level  TINYINT     NOT NULL COMMENT '1一般 2高危',
  summary     TEXT        COMMENT '脱敏摘要(不加密，供授权角色查看；原始对话在 emotion_message 中加密)',
  status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0待处理 1已跟进',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关怀提醒表';

-- ---------- 手作项目 ----------
CREATE TABLE IF NOT EXISTS craft_project (
  id           BIGINT       NOT NULL,
  inheritor_id BIGINT       NOT NULL COMMENT '上传传承人',
  title        VARCHAR(100) NOT NULL,
  intro        TEXT,
  culture_bg   TEXT         COMMENT '文化背景',
  level        TINYINT      NOT NULL DEFAULT 1 COMMENT '1入门 2进阶 3高阶',
  est_minutes  INT          DEFAULT NULL COMMENT '预计时长(分钟)',
  category     VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '技艺品类，取值见 dict_category.label',
  therapy_tags VARCHAR(255) COMMENT '疗愈效果标签,逗号分隔,词表见 dict_therapy_tags',
  cover_url    VARCHAR(255),
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审 1上架 2下架',
  create_time  DATETIME     DEFAULT NULL,
  update_time  DATETIME     DEFAULT NULL,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_inheritor (inheritor_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手作项目表';

CREATE TABLE IF NOT EXISTS craft_step (
  id         BIGINT       NOT NULL,
  project_id BIGINT       NOT NULL,
  step_no    INT          NOT NULL,
  title      VARCHAR(100),
  guide      TEXT         COMMENT '引导语',
  audio_url  VARCHAR(255) COMMENT '引导语音频',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手作步骤表';

CREATE TABLE IF NOT EXISTS craft_material (
  id         BIGINT       NOT NULL,
  project_id BIGINT       NOT NULL,
  name       VARCHAR(100) NOT NULL,
  qty        VARCHAR(50),
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手作材料表';

-- ---------- 推荐 ----------
CREATE TABLE IF NOT EXISTS craft_recommendation (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  session_id  BIGINT      DEFAULT NULL,
  mood        VARCHAR(32) COMMENT '情绪 code，取值见 dict_mood.code',
  reason      TEXT        COMMENT '整体理由(加密)',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐主表';

CREATE TABLE IF NOT EXISTS craft_recommendation_item (
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
CREATE TABLE IF NOT EXISTS craft_session (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  project_id  BIGINT      NOT NULL,
  status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0待开始 1进行中 2暂停 3完成 4放弃；创建即 1，状态机仅 pause/resume/finish/abandon',
  current_step INT        NOT NULL DEFAULT 1 COMMENT '当前步骤序号',
  start_time  DATETIME    DEFAULT NULL,
  end_time    DATETIME    DEFAULT NULL,
  flow_seconds INT        DEFAULT 0 COMMENT '心流累计时长(秒)；占比 = flow_seconds/(end_time-start_time)',
  create_time DATETIME    DEFAULT NULL,
  update_time DATETIME    DEFAULT NULL,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作会话表';

-- ---------- 作品与互动 ----------
CREATE TABLE IF NOT EXISTS work (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL COMMENT '归属者（匿名仅展示层行为，后端必须存 user_id）',
  project_id  BIGINT       DEFAULT NULL,
  session_id  BIGINT       DEFAULT NULL,
  title       VARCHAR(100),
  cover_url   VARCHAR(255),
  message     TEXT         COMMENT 'AI 寄语',
  visible     TINYINT      NOT NULL DEFAULT 1 COMMENT '0私密 1公开 2仅小组',
  anonymous   TINYINT      NOT NULL DEFAULT 0 COMMENT '0显示昵称 1匿名展示',
  like_count  INT          NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0待审 1已发布 2下架',
  create_time DATETIME     DEFAULT NULL,
  update_time DATETIME     DEFAULT NULL,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品表';

CREATE TABLE IF NOT EXISTS work_comment (
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

CREATE TABLE IF NOT EXISTS work_like (
  id          BIGINT   NOT NULL,
  work_id     BIGINT   NOT NULL,
  user_id     BIGINT   NOT NULL,
  type        TINYINT  NOT NULL DEFAULT 1 COMMENT '1点赞 0收藏（两种类型可并存）',
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  deleted     TINYINT  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_work_user_type (work_id, user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品点赞/收藏表';

-- ---------- 互助小组 ----------
CREATE TABLE IF NOT EXISTS support_group (
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

CREATE TABLE IF NOT EXISTS group_member (
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
CREATE TABLE IF NOT EXISTS inheritor_profile (
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
-- 例外：只追加，不更新不删除，故只保留 create_time、无 deleted
CREATE TABLE IF NOT EXISTS ai_audit_log (
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

-- ============================================================
-- 老库补丁区（幂等，可重复执行）
--
-- 上面的建表语句一律是 CREATE TABLE IF NOT EXISTS，所以「修改列定义」（如改 DEFAULT、
-- 改 COMMENT）不会作用到已存在的表 —— 这类变更必须在这里显式 ALTER 一次。
-- 本区放在 schema.sql 末尾而不是 reset.sql：reset.sql 只负责重建空库，
-- 它执行时 user 表尚不存在，把 ALTER 放那里必然报 ERROR 1146。
-- ============================================================

-- T0.2b：user.can_contact 默认值 1 → 0（咨询师联系用户须由用户单独授权）
ALTER TABLE user MODIFY COLUMN can_contact TINYINT NOT NULL DEFAULT 0
  COMMENT '0不允许 1允许心理咨询师联系；默认 0，需用户单独授权';
