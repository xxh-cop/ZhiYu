-- ============================================================
-- 「织愈」初始化数据（幂等：可重复执行不报错）
--   * 全部使用 INSERT ... ON DUPLICATE KEY UPDATE，重复执行只覆盖不报错
--   * 主键手动指定小整数（1..4 / 101.. / 1001..），
--     与应用侧雪花 ID（ASSIGN_ID）不会冲突（雪花位数远大于 10^4）
--   * 演示用数据；生产环境请重新生成密码与内容
-- ============================================================

-- 同 schema.sql：Windows 版客户端默认 gbk，必须先声明 utf8mb4，否则中文乱码
SET NAMES utf8mb4;

USE zhiyu;

-- ---------- 情绪字典（5 条） ----------
-- mood 统一英文 code；中文只在前端展示层映射。
-- therapy_tags = 该情绪优先推荐的「疗愈效果标签」，词表见 sql/dict-therapy-tags.sql，
-- 与 craft_project.therapy_tags 同一套词汇，供规则推荐匹配。
INSERT INTO dict_mood (id, code, label, therapy_tags, sort_no, create_time, update_time, deleted) VALUES
(1, 'anxious',   '焦虑', '静心,专注,安抚',         1, NOW(), NOW(), 0),
(2, 'stressed',  '压力', '放空,释放,触觉安抚',     2, NOW(), NOW(), 0),
(3, 'depressed', '低落', '成就感,陪伴,创造表达',   3, NOW(), NOW(), 0),
(4, 'angry',     '烦躁', '物理宣泄,释放,触觉安抚', 4, NOW(), NOW(), 0),
(5, 'tired',     '疲惫', '轻量成就,安抚,陪伴',   5, NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  label        = new.label,
  therapy_tags = new.therapy_tags,
  sort_no      = new.sort_no,
  update_time  = new.update_time,
  deleted      = new.deleted;

-- ---------- 内置账号 ----------
-- 明文密码（仅本地演示用，上线前必须全部重置）：
--   admin / admin123
--   inheritor01 / inherit123
--   counselor01 / couns123
--   student01 / stu12345
-- 哈希由 spring-security-crypto BCryptPasswordEncoder（strength=10, $2a）生成，
-- 并已用 matches() 对正确密码返回 true、对错误密码返回 false 完成自校验。
INSERT INTO user (id, username, password, nickname, role, status, create_time, update_time, deleted) VALUES
(1, 'admin',       '$2a$10$Yy.Y2aYDe8nWvGZeeHoglOmSzr3akq9KjqXxANwILZx/E64TteNf6', '平台管理员',     3, 1, NOW(), NOW(), 0),
(2, 'inheritor01', '$2a$10$..MRYVCls3pPx7YVSaicLONcPGX2QjGmni6tiq6cP1MWQjaCI9Jgm', '赣绣王老师',     1, 1, NOW(), NOW(), 0),
(3, 'counselor01', '$2a$10$.P0kxS2d7DePY3Fe5CaP.eX3JwJTdO9oiyDLMDh9iXQRMhI7hHOoO', '心理中心李老师', 2, 1, NOW(), NOW(), 0),
(4, 'student01',   '$2a$10$oHN82aQYzGcPPEURa3ULd.k7F7QpINqvARIuW/iLQ.r9ncqAujAHq', '小织',           0, 1, NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  password    = new.password,
  nickname    = new.nickname,
  role        = new.role,
  status      = new.status,
  update_time = new.update_time,
  deleted     = new.deleted;

-- ---------- 传承人资料 ----------
INSERT INTO inheritor_profile (id, user_id, skill, intro, honor, verified, create_time, update_time, deleted) VALUES
(1, 2, '赣绣', '从事赣绣四十余年，擅长针脚细腻的丝线绣品。', '省级非遗代表性传承人', 1, NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  user_id     = new.user_id,
  skill       = new.skill,
  intro       = new.intro,
  honor       = new.honor,
  verified    = new.verified,
  update_time = new.update_time,
  deleted     = new.deleted;

-- ---------- 手作项目（已上架示例） ----------
-- category     = 技艺品类，取值见 sql/dict-category.sql 的 label，仅展示用
-- therapy_tags = 疗愈效果标签，取值见 sql/dict-therapy-tags.sql 的 label，参与推荐匹配
INSERT INTO craft_project (id, inheritor_id, title, intro, culture_bg, level, est_minutes, category, therapy_tags, cover_url, status, create_time, update_time, deleted) VALUES
(101, 2, '解压扎染方巾', '用扎染的自然晕染，把焦虑折进布里、染在手中。',   '扎染是国家级非遗，通过捆扎、浸染形成不可预测的纹样。',           1, 45, '扎染', '放空,触觉安抚',          '/static/covers/zharan.jpg',  1, NOW(), NOW(), 0),
(102, 2, '赣绣小书签',   '一针一线，在缓慢的穿引中把注意力拉回当下。',   '赣绣是江西地方刺绣，针法细腻、色彩温润。',                       1, 60, '赣绣', '专注,静心,情绪稳定',     '/static/covers/ganxiu.jpg',  1, NOW(), NOW(), 0),
(103, 2, '剪纸释压团花', '一刀一纸，把烦恼剪进对称的纹样里。',           '剪纸在江西多地节庆中广泛使用，讲究对称与节奏。',                 1, 30, '剪纸', '成就感,创造表达,专注',   '/static/covers/jianzhi.jpg', 1, NOW(), NOW(), 0),
(104, 2, '陶艺捏塑杯垫', '双手与泥土对话，在揉捏中释放紧绷。',           '陶艺手工塑形，触觉反馈强，适合情绪焦躁时使用。',                 2, 50, '陶艺', '物理宣泄,释放,触觉安抚', '/static/covers/taoyi.jpg',   1, NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  inheritor_id = new.inheritor_id,
  title        = new.title,
  intro        = new.intro,
  culture_bg   = new.culture_bg,
  level        = new.level,
  est_minutes  = new.est_minutes,
  category     = new.category,
  therapy_tags = new.therapy_tags,
  cover_url    = new.cover_url,
  status       = new.status,
  update_time  = new.update_time,
  deleted      = new.deleted;

-- ---------- 步骤（以扎染为例） ----------
INSERT INTO craft_step (id, project_id, step_no, title, guide, audio_url, create_time, update_time, deleted) VALUES
(1001, 101, 1, '折布与捆扎', '把白色方巾按你喜欢的方式折叠，用棉线紧紧扎紧。不必追求整齐，随心意即可。', '/static/audio/101-1.mp3', NOW(), NOW(), 0),
(1002, 101, 2, '调配染料',   '把染料缓缓倒入水盆，观察颜色在水中晕开。',                                     '/static/audio/101-2.mp3', NOW(), NOW(), 0),
(1003, 101, 3, '浸染',       '把扎好的布浸入染料，静静等待 10 分钟。',                                       '/static/audio/101-3.mp3', NOW(), NOW(), 0),
(1004, 101, 4, '拆开与晾晒', '拆开棉线，展开属于你的独一无二纹样。',                                         '/static/audio/101-4.mp3', NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  project_id  = new.project_id,
  step_no     = new.step_no,
  title       = new.title,
  guide       = new.guide,
  audio_url   = new.audio_url,
  update_time = new.update_time,
  deleted     = new.deleted;

-- ---------- 互助小组 ----------
INSERT INTO support_group (id, name, description, status, create_time, update_time, deleted) VALUES
(1, '考试周搭子',   '一起熬过期末周的手作搭子们',       1, NOW(), NOW(), 0),
(2, '夜猫子疗愈所', '晚上睡不着时来做点安静的手作',     1, NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  name        = new.name,
  description = new.description,
  status      = new.status,
  update_time = new.update_time,
  deleted     = new.deleted;
