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
  intro       TEXT         DEFAULT NULL COMMENT '个人简介',
  craft_preferences VARCHAR(255) DEFAULT NULL COMMENT '手作偏好标签,逗号分隔',
  can_contact TINYINT      NOT NULL DEFAULT 0 COMMENT '0不允许 1允许心理咨询师联系；默认 0，需用户单独授权',
  privacy_agreed TINYINT   NOT NULL DEFAULT 0 COMMENT '隐私政策是否同意：0未同意 1已同意',  baseline_nudge_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '情绪基线偏离提醒开关（1 开 0 关，AM1）',
  baseline_nudge_last_at DATETIME DEFAULT NULL COMMENT '基线提醒上次提示时间（冷却用）',
  baseline_nudge_last_level VARCHAR(16) DEFAULT NULL COMMENT '基线提醒上次提示档位',
  stuck_guide_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '卡点引导开关（1 开 0 关；同一步骤反复停留时给出可选动作建议的轻提示，2026-11-05）',

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
  summary     TEXT        DEFAULT NULL COMMENT '会话总结(加密)',
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
  risk_level  TINYINT     NOT NULL COMMENT '1关注 2高危 3紧急',
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
    video_url VARCHAR(255) DEFAULT NULL,
  cover_url    VARCHAR(255),
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审 1上架 2下架 3归档（2/3 不可回 1，恢复上线走新建送审）',
  audit_reason VARCHAR(300)  DEFAULT NULL COMMENT '审核/下架/删除理由（AY1 2026-11-05；驳回理由同列落库）',
  takedown_kind VARCHAR(16)  DEFAULT NULL COMMENT '下架类型：compliance 合规 / operational 运营（2026-11-05 分层下架）',
  takedown_at  DATETIME      DEFAULT NULL COMMENT '下架时间',
  archive_at   DATETIME      DEFAULT NULL COMMENT '归档时间/截止：运营下架必填；合规下架窗口结束转归档时回填',
  export_until DATETIME      DEFAULT NULL COMMENT '私有导出/只读窗口截止（合规下架 = takedown_at + zhiyu.takedown.compliance.export-window-hours）',
  notice_key   VARCHAR(64)   DEFAULT NULL COMMENT '提示语模板 key，对应 resources/notices/<key>.md',
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
  license_status VARCHAR(16) NOT NULL DEFAULT 'none' COMMENT '音频授权：none/cc0/owned/licensed/pending_review',
  attribution  VARCHAR(300) DEFAULT NULL COMMENT '音频署名信息（CC/授权来源）',
  asset_status VARCHAR(16)  DEFAULT NULL COMMENT '音频资产状态：ready 可播 / missing 缺失 / disabled 停用；NULL=无音频资产',
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

CREATE TABLE IF NOT EXISTS craft_recommend_event (
  id                BIGINT      NOT NULL,
  user_id           BIGINT      NOT NULL COMMENT '点击用户',
  recommendation_id BIGINT      NOT NULL COMMENT 'craft_recommendation.id',
  project_id        BIGINT      NOT NULL COMMENT '被点击项目',
  event_type        VARCHAR(16) NOT NULL DEFAULT 'CLICK' COMMENT 'CLICK；预留 EXPOSURE',
  source            VARCHAR(16) NOT NULL COMMENT 'HOME / RECOMMEND',
  create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recommend_event (user_id, recommendation_id, project_id, event_type),
  KEY idx_recommend_event_time (event_type, create_time),
  KEY idx_recommend_event_rec (recommendation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐点击事件表';

-- ---------- 创作会话 ----------
CREATE TABLE IF NOT EXISTS craft_session (
id          BIGINT      NOT NULL,
user_id     BIGINT      NOT NULL,
project_id  BIGINT      NOT NULL,
emotion_session_id BIGINT DEFAULT NULL COMMENT '关联的情绪会话 emotion_session.id，可空',
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

-- ---------- 创作会话行为事件（2026-11-05 心流度量配套） ----------
CREATE TABLE IF NOT EXISTS craft_session_event (
  id               BIGINT       NOT NULL COMMENT '应用侧雪花 ID（ASSIGN_ID）',
  session_id       BIGINT       NOT NULL COMMENT 'craft_session.id',
  user_id          BIGINT       NOT NULL COMMENT '归属用户（服务端按会话填充，不信任客户端）',
  craft_project_id BIGINT       NOT NULL COMMENT '所属项目（服务端按会话填充）',
  event_type       VARCHAR(32)  NOT NULL COMMENT 'step_enter/step_leave/step_advance/step_backtrack/heartbeat/audio_play/visibility_change/app_lifecycle/dwell_stagnation/suspected_away/breathing_prompt/breathing_responded/export/session_finish/session_abandon/session_archive/step_gate_blocked（服务端派生，心跳被下架闸口拒绝，每会话至多一条）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作会话行为事件表（心流度量配套；焦躁双信号 dwell_stagnation 与 step_backtrack 分开记录）';

-- ---------- 作品与互动 ----------
CREATE TABLE IF NOT EXISTS work (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL COMMENT '归属者（匿名仅展示层行为，后端必须存 user_id）',
  project_id  BIGINT       DEFAULT NULL,
  session_id  BIGINT       DEFAULT NULL,
title       VARCHAR(100),
cover_url   VARCHAR(255),
media_type  VARCHAR(10)  NOT NULL DEFAULT 'image' COMMENT '媒体类型：image/video/text',
video_url   VARCHAR(255) DEFAULT NULL COMMENT '视频 URL（media_type=video 时有值）',
message     TEXT         COMMENT 'AI 寄语',
  visible     TINYINT      NOT NULL DEFAULT 1 COMMENT '0私密 1公开 2仅小组',
  anonymous   TINYINT      NOT NULL DEFAULT 1 COMMENT '0显示昵称 1匿名展示；默认匿名',
  like_count  INT          NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0待审 1已发布 2下架',
  audit_reason VARCHAR(255) DEFAULT NULL COMMENT '审核理由；驳回必填，通过可空',
  soft_demote_until DATETIME DEFAULT NULL COMMENT '软档降权截止（2026-11-07 处置链；NULL=不降权；处置链开关关闭时读侧忽略本列）',
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
  audit_reason VARCHAR(255) DEFAULT NULL COMMENT '审核理由；驳回必填，通过可空',
  soft_demote_until DATETIME DEFAULT NULL COMMENT '软档降权截止（2026-11-07 处置链；NULL=不降权；处置链开关关闭时读侧忽略本列）',
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
  user_id     BIGINT      DEFAULT NULL COMMENT '关联用户ID；迁移前历史行为 NULL，新写入必须带',
  scene       VARCHAR(30) NOT NULL COMMENT 'chat/recommend/message',
  raw_input   TEXT        COMMENT '输入(加密)',
  ai_output   TEXT        COMMENT '输出(加密)',
  action      VARCHAR(20) NOT NULL COMMENT 'pass/review/block',
  reason      VARCHAR(255),
  ref_type    VARCHAR(30) DEFAULT NULL COMMENT '关联对象类型：work/comment 等（2026-11-04 起新写入携带）',
  ref_id      BIGINT      DEFAULT NULL COMMENT '关联对象 id（雪花）',
  create_time DATETIME    DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_scene (scene),
  KEY idx_audit_user (user_id),
  KEY idx_audit_ref (ref_type,ref_id)
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

-- T0.6a：work.anonymous 默认值 0 → 1（默认匿名发布，符合产品定位；与 openapi.yaml WorkPublishReq.anonymous default=true 对齐）
ALTER TABLE work MODIFY COLUMN anonymous TINYINT NOT NULL DEFAULT 1
  COMMENT '0显示昵称 1匿名展示；默认匿名';

-- 20261026：ai_audit_log 增加 user_id。
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，本区要求可重复执行，故不在 schema.sql 内 ALTER；
-- 已有库请执行 sql/migrations/20261026_ai_audit_user_id.sql（含备份与重复执行检查说明）。


-- Growth modules: encrypted personal records, scheduled programs and capacity-safe bookings.
CREATE TABLE IF NOT EXISTS wellbeing_record (
 id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, kind VARCHAR(24) NOT NULL,
 record_key VARCHAR(100) NOT NULL, payload TEXT NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_wellbeing_record (user_id, kind, record_key),
 KEY idx_wellbeing_owner (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS learning_program (
 id BIGINT PRIMARY KEY, owner_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL,
 title VARCHAR(100) NOT NULL, description TEXT NOT NULL, location VARCHAR(200) NOT NULL,
 co_leader VARCHAR(100) NOT NULL, materials VARCHAR(1000) NOT NULL,
 start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
 capacity INT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 KEY idx_program_schedule (status,start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS program_booking (
 id BIGINT PRIMARY KEY, program_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'BOOKED',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_program_member (program_id,user_id), KEY idx_booking_owner (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS craft_safety (
 id BIGINT PRIMARY KEY, project_id BIGINT NOT NULL UNIQUE, medium VARCHAR(20) NOT NULL,
 level VARCHAR(16) NOT NULL, hazards VARCHAR(300) NOT NULL, guidance VARCHAR(1000) NOT NULL,
 source VARCHAR(300) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS media_asset (
 id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, storage_key VARCHAR(180) NOT NULL, size_bytes BIGINT NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL, deleted TINYINT NOT NULL DEFAULT 0
);

-- Additive migration; no existing records are rewritten.
CREATE TABLE IF NOT EXISTS campus_verification (
 id BIGINT NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE,
 school VARCHAR(100) NOT NULL, college VARCHAR(100) NOT NULL,
 identity_cipher TEXT NOT NULL, status VARCHAR(20) NOT NULL,
 review_reason VARCHAR(300) NOT NULL DEFAULT '', reviewer_id BIGINT DEFAULT NULL,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL, deleted TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS care_case (
 id BIGINT NOT NULL PRIMARY KEY, reminder_id BIGINT NOT NULL UNIQUE,
 owner_id BIGINT DEFAULT NULL, state VARCHAR(20) NOT NULL DEFAULT 'OPEN',
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL, deleted TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS care_followup (
 id BIGINT NOT NULL PRIMARY KEY, reminder_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 action VARCHAR(20) NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 deleted TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS admin_operation_log (
 id BIGINT NOT NULL PRIMARY KEY, actor_id BIGINT NOT NULL, action VARCHAR(100) NOT NULL,
 target_id BIGINT DEFAULT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 deleted TINYINT NOT NULL DEFAULT 0
);


CREATE TABLE IF NOT EXISTS course_lesson (
 id BIGINT NOT NULL PRIMARY KEY,
 program_id BIGINT NOT NULL,
 chapter VARCHAR(100) NOT NULL,
 title VARCHAR(100) NOT NULL,
 position INT NOT NULL,
 content TEXT NOT NULL,
 project_id BIGINT DEFAULT NULL,
 published TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL,
 update_time DATETIME NOT NULL,
 deleted TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS lesson_progress (
 id BIGINT NOT NULL PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 completed TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL,
 update_time DATETIME NOT NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_lesson_user (lesson_id,user_id)
);

CREATE TABLE IF NOT EXISTS group_message (
 id BIGINT NOT NULL PRIMARY KEY,
 group_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 content TEXT NOT NULL,
 create_time DATETIME NOT NULL,
 update_time DATETIME NOT NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 KEY idx_group_message (group_id,deleted,id)
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
CREATE TABLE IF NOT EXISTS heritage_catalog_version (
 id BIGINT NOT NULL,
 version_no INT NOT NULL COMMENT '发布版本号，从 1 递增',
 payload TEXT NOT NULL COMMENT '完整目录快照(JSON)',
 note VARCHAR(200) DEFAULT NULL COMMENT '发布说明',
 actor_id BIGINT NOT NULL COMMENT '发布管理员',
 create_time DATETIME DEFAULT NULL,
 update_time DATETIME DEFAULT NULL,
 deleted TINYINT NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_heritage_version (version_no),
 KEY idx_heritage_version_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非遗目录发布版本';
CREATE TABLE IF NOT EXISTS file_cleanup_job (
 id BIGINT PRIMARY KEY, storage_kind VARCHAR(16) NOT NULL, storage_key VARCHAR(255) NOT NULL,
 attempts INT NOT NULL DEFAULT 0, state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_cleanup_key (storage_kind,storage_key)
);

-- ---------- L4 Companion：统一练习会话、可选记忆、AI 运行元数据 ----------
-- practice_session 将情绪、非遗练习、正念/绘色等模式挂到同一次练习；
-- context_cipher 只存加密后的限制/偏好，不存原始对话。
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

-- 仅存用户明确同意的偏好摘要，不存原始对话或高危细节。
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

-- 只追加的 AI 运行元数据；不存原文、思维链或工具原始参数。
CREATE TABLE IF NOT EXISTS ai_trace (
  id                  BIGINT      NOT NULL,
  trace_id            VARCHAR(64) NOT NULL,
  user_id             BIGINT      DEFAULT NULL,
  practice_session_id BIGINT      DEFAULT NULL,
  scene               VARCHAR(30) NOT NULL,
  model               VARCHAR(80) DEFAULT NULL,
  provider            VARCHAR(20) DEFAULT NULL COMMENT '实际服务 provider（deepseek/ollama/mock；null=存量或未发起调用）',
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

-- ---------- 通知（2026-11-04：审核/预约/小组/关怀事件触达） ----------
CREATE TABLE IF NOT EXISTS notification (
  id            BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL COMMENT '接收者（本人数据隔离）',
  type          VARCHAR(30)  NOT NULL COMMENT 'WORK_AUDIT/COMMENT_AUDIT/BOOKING_REPLY/GROUP_JOIN/CARE_REMINDER',
  title         VARCHAR(120) NOT NULL,
  content       VARCHAR(500) DEFAULT NULL COMMENT '脱敏正文；关怀类只含引导语，不含敏感内容',
  ref_type      VARCHAR(30)  DEFAULT NULL COMMENT '关联对象类型：work/comment/booking/group/care_reminder',
  ref_id        BIGINT       DEFAULT NULL COMMENT '跳转目标 id（雪花，前端按字符串处理）；COMMENT_AUDIT 为评论所属作品 id',
  sub_ref_id    BIGINT       DEFAULT NULL COMMENT '二级关联对象 id（如评论 id 锚点，2026-11-04 S2 新增）',
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

-- ---------- 导出审计（2026-11-04 N2：只追加，审计语义同 ai_audit_log） ----------
CREATE TABLE IF NOT EXISTS admin_export_log (
  id            BIGINT       NOT NULL,
  actor_id      BIGINT       NOT NULL COMMENT '导出人（管理员）',
  actor_role    TINYINT      NOT NULL COMMENT '导出时角色',
  target_type   VARCHAR(20)  NOT NULL COMMENT 'users/works/comments',
  filter_summary VARCHAR(500) DEFAULT NULL COMMENT '筛选条件脱敏摘要',
  field_list    VARCHAR(500) NOT NULL COMMENT '导出字段（黑名单校验后）',
  record_count  INT          NOT NULL DEFAULT 0,
  purpose       VARCHAR(200) NOT NULL COMMENT '导出用途（必填）',
  batch_no      VARCHAR(32)  NOT NULL COMMENT '导出批次号（文件名/文件头引用）',
  alert_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1超阈值告警',
  create_time   DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_export_batch (batch_no),
  KEY idx_export_actor (actor_id,create_time),
  KEY idx_export_target (target_type,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出审计留痕（只追加）';

-- ---------- 对话上下文滚动摘要（P1b 2026-11-04；2026-11-05 阶段 2 起 rolling-summary 默认开启） ----------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话上下文滚动摘要（P1b；2026-11-05 起默认开启）';

-- ---------- 危机路径留存（AJ 2026-11-04：只存脱敏摘要与危机等级，绝不存原文） ----------
CREATE TABLE IF NOT EXISTS crisis_session_log (
  id             BIGINT       NOT NULL COMMENT '雪花 id（应用侧 ASSIGN_ID，不设 AUTO_INCREMENT）',
  user_id        BIGINT       NOT NULL COMMENT '触发用户',
  session_id     BIGINT       DEFAULT NULL COMMENT '关联会话/来源标识（情绪会话或练习会话，可空）',
  scene          VARCHAR(30)  NOT NULL COMMENT '触发场景（chat/companion/asr 等）',
  risk_level     TINYINT      NOT NULL DEFAULT 2 COMMENT '危机等级（1关注 2高危 3紧急，与 care_reminder 口径一致）',
  summary_cipher TEXT         NOT NULL COMMENT '脱敏摘要密文（应用层 AES；规则式概括，绝不含用户/AI 原文）',
  create_time    DATETIME     NOT NULL,
  update_time    DATETIME     NOT NULL,
  deleted        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_crisis_user (user_id, deleted),
  KEY idx_crisis_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='危机路径留存（降级：只存脱敏摘要，不存原文；仅授权关怀角色可见）';

-- ---------- 审核软档处置链（2026-11-07：软档台账 + 抽检池一体；详见 docs/moderation-soft-tier-disposition-chain-20261107.md） ----------
CREATE TABLE IF NOT EXISTS moderation_soft_review (
  id            BIGINT       NOT NULL,
  target_type   VARCHAR(10)  NOT NULL COMMENT 'work/comment',
  target_id     BIGINT       NOT NULL COMMENT '对象 id（雪花）',
  author_id     BIGINT       NOT NULL COMMENT '作者（作者分层抽样与本人数据口径）',
  ai_tendency   VARCHAR(20)  NOT NULL DEFAULT 'SOFT_CONCERN' COMMENT 'AI 建议倾向（当前固定软档）',
  narrative     VARCHAR(20)  DEFAULT NULL COMMENT '窄门 narrative 值留证（self_disclosure）',
  audit_log_id  BIGINT       DEFAULT NULL COMMENT '关联 ai_audit_log 建议记录，不重复存原文',
  sampled       TINYINT      NOT NULL DEFAULT 0 COMMENT '0未抽中 1抽中进人工池',
  sample_layer  VARCHAR(30)  DEFAULT NULL COMMENT '抽样分层：mandatory_lexicon/mandatory_riskpoint/mandatory_author_first/random/overflow_*',
  pool_status   VARCHAR(20)  NOT NULL DEFAULT 'NOT_SAMPLED' COMMENT 'NOT_SAMPLED/PENDING/ESCALATED/CLEAR/UPHOLD/BLOCK/EXPIRED',
  reviewer_id   BIGINT       DEFAULT NULL COMMENT '裁决人（管理员）',
  reason        VARCHAR(255) DEFAULT NULL COMMENT '裁决理由（BLOCK 必填）',
  sla_due_at    DATETIME     DEFAULT NULL COMMENT '复核时限（仅 sampled=1；入池 + sla-hours）',
  reviewed_at   DATETIME     DEFAULT NULL COMMENT '裁决时间',
  demote_until  DATETIME     DEFAULT NULL COMMENT '降权截止（台账侧事实源；热路径冗余在 work/work_comment.soft_demote_until）',
  appeal_status VARCHAR(20)  DEFAULT NULL COMMENT 'NONE/FILED/RESOLVED',
  appeal_reason VARCHAR(200) DEFAULT NULL COMMENT '申诉理由（2026-11-07 补；必填过输入侧安全过滤后落库）',
  notified      TINYINT      NOT NULL DEFAULT 0 COMMENT '降权通知是否已发（幂等）',
  create_time   DATETIME     DEFAULT NULL,
  update_time   DATETIME     DEFAULT NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_soft_target (target_type, target_id),
  KEY idx_pool_sla (pool_status, sla_due_at),
  KEY idx_soft_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='软档处置台账/抽检池（处置链开关 zhiyu.moderation.soft-tier.disposition.enabled 默认 false）';

-- ---------- 软档影子模式观测台账（2026-11-07：只观测「若开启软档会怎样」，不处置；开关 zhiyu.moderation.soft-tier.shadow.enabled 默认 false） ----------
CREATE TABLE IF NOT EXISTS moderation_soft_shadow (
  id            BIGINT       NOT NULL,
  target_type   VARCHAR(10)  NOT NULL COMMENT 'work/comment',
  target_id     BIGINT       NOT NULL COMMENT '对象 id（雪花）',
  author_id     BIGINT       NOT NULL COMMENT '作者（灰度哈希分群与本人数据口径）',
  judge_status  VARCHAR(12)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待重试/OK 已判定/UNAVAILABLE 终态不可用（达重试上限）',
  tendency      VARCHAR(20)  DEFAULT NULL COMMENT '若开启口径的判定结果（含窄门后；judge_status=OK 时有值）',
  narrative     VARCHAR(20)  DEFAULT NULL COMMENT '窄门 narrative 原始枚举值留证（仅枚举 token，非内容）',
  audit_log_id  BIGINT       DEFAULT NULL COMMENT '最近一次判定关联的 ai_audit_log（原文加密存那里，本表不存原文）',
  provider      VARCHAR(30)  NOT NULL DEFAULT 'unknown' COMMENT '判定归属 provider（deepseek=云端生产/本地演示另值；unknown=2026-11-07 加列前存量或尚无成功判定）',
  model         VARCHAR(80)  NOT NULL DEFAULT 'unknown' COMMENT '判定归属模型名（与 provider 同源；unknown 同上）',
  attempts      INT          NOT NULL DEFAULT 0 COMMENT '已尝试次数（熔断窗失败不一次定终态，重试至 max-attempts 才落 UNAVAILABLE）',
  last_attempt_at DATETIME   DEFAULT NULL COMMENT '最后尝试时间',
  target_create_time DATETIME DEFAULT NULL COMMENT '对象送审时间（统计时间窗口径）',
  create_time   DATETIME     DEFAULT NULL,
  update_time   DATETIME     DEFAULT NULL,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shadow_target (target_type, target_id),
  KEY idx_shadow_target_ct (target_create_time),
  KEY idx_shadow_retry (judge_status, attempts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='软档影子模式观测台账（只观测不处置：不写业务软档字段/不影响审核结论/不入抽检池/不发通知）';
