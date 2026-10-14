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
-- 2026-10-08 修复：旧 hash 与明文密码不匹配，已全部重新生成。
INSERT INTO user (id, username, password, nickname, role, status, create_time, update_time, deleted) VALUES
(1, 'admin',       '$2a$10$s9J7wtk3oYoLh9VDO8vz3O7huqZbalPVIE5FYT3QLXkFQ5cLJSrOO', '平台管理员',     3, 1, NOW(), NOW(), 0),
(2, 'inheritor01', '$2a$10$L0W2j1URsZz52YM7ed2aDOL8pKV0mgLwJqG6HtymI5eRyIHkZDoEa', '赣绣王老师',     1, 1, NOW(), NOW(), 0),
(3, 'counselor01', '$2a$10$TsnsqzwEd8L32V0UMbdrAuix6P4oPtQKG/9uAZ1XTllpjF6Go1wTO', '心理中心李老师', 2, 1, NOW(), NOW(), 0),
(4, 'student01',   '$2a$10$fFLe25g4bZV5rKK.j5nnB.N9CKY3l2fqpOnJnpL3ZafPQKzdylyQa', '小织',           0, 1, NOW(), NOW(), 0) AS new
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

-- ---------- 步骤（赣绣小书签 / 剪纸释压团花） ----------
INSERT INTO craft_step (id, project_id, step_no, title, guide, audio_url, create_time, update_time, deleted) VALUES
(2001, 102, 1, '准备绣线', '挑选你喜欢的颜色，把线穿进针孔。', NULL, NOW(), NOW(), 0),
(2002, 102, 2, '起针',     '从布的反面起针，让线头藏在背面。', NULL, NOW(), NOW(), 0),
(2003, 102, 3, '绣制图案', '沿着图案慢慢走针，不用追求完美。', NULL, NOW(), NOW(), 0),
(2004, 102, 4, '收针与整理', '把线头藏好，书签就完成了。',     NULL, NOW(), NOW(), 0),
(3001, 103, 1, '折纸',     '把正方形红纸对折三次，形成一个小三角。', NULL, NOW(), NOW(), 0),
(3002, 103, 2, '画稿',     '在折好的纸上画上你想要的图案。',         NULL, NOW(), NOW(), 0),
(3003, 103, 3, '剪制',     '沿着画线慢慢剪，注意不要剪断连接处。',   NULL, NOW(), NOW(), 0),
(3004, 103, 4, '展开',     '轻轻展开纸张，看看你的团花。',           NULL, NOW(), NOW(), 0) AS new
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

-- Growth modules have no fabricated records or scheduled events.
-- Safety metadata is completed by project owners before being labelled verified.


-- 第四阶段：原创入门内容（完整来源见 catalog/starter-crafts.json）
-- 织愈原创入门内容 v2026-10-11.1；仅新增，不覆盖已有项目。
SET NAMES utf8mb4;
USE zhiyu;
START TRANSACTION;

INSERT INTO dict_category(id,code,label,sort_no,create_time,update_time,deleted) SELECT 940090,'paper_art','纸艺',8,NOW(),NOW(),0 WHERE NOT EXISTS(SELECT 1 FROM dict_category WHERE code='paper_art' OR label='纸艺' OR id=940090);
SET @starter_owner=(SELECT MIN(id) FROM user WHERE role=3 AND status=1 AND deleted=0);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940001,@starter_owner,'折扇小景','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','本练习为织愈原创日常手作，不宣称属于特定非遗传承体系。',1,10,'纸艺','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940001);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000101,940001,1,'折出第一道边','把长方形纸横放，从短边向内折约两指宽，压平折痕。
遇到困难：纸边不齐也可以继续，保持后续宽度相近。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940001) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940001 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000102,940001,2,'正反交替','翻转纸张，再折相近宽度；反复正折、反折，直到整张纸成为纸条。
遇到困难：折错方向时展开最后一折再来，不必全部重做。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940001) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940001 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000103,940001,3,'捏住扇柄','握住纸条的一端，另一端轻轻展开成半圆；用手捏住底部即可。
遇到困难：展开困难先松开几道折痕，避免用力撕扯。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940001) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940001 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000104,940001,4,'留一片风景','选择自己喜欢的展开角度，放在桌上观察光影；收拢后夹入本子。
遇到困难：不能站立很正常，可平放保存。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940001) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940001 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000151,940001,'普通纸','1–2 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940001) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000151);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940001,940001,'纸','LOW','注意纸张边缘；使用笔时避免尖端朝向身体。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940001) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940001);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940002,@starter_owner,'双叶剪纸','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','背景参考：福建省政府：中国剪纸文化介绍（背景参考）。本练习是独立编写的入门活动，不代表相关机构认证。',1,15,'纸艺','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940002);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000201,940002,1,'纸张对折','把纸对折，折边朝左，在折边上标出叶片顶点与底点。
遇到困难：纸太厚时改用一张普通纸，勿叠多层剪。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940002) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940002 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000202,940002,2,'画半片叶','从上点向右画鼓起的弧线，再回到下点，留下连接的折边。
遇到困难：弧线画宽些，避免细长形状容易断。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940002) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940002 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000203,940002,3,'沿外线剪','坐稳，将手指放在剪线外，沿弧线慢慢剪，保留折边。
遇到困难：剪断时把两半做成并排双叶，不必重新开始。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940002) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940002 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000204,940002,4,'打开观察','轻轻展开，对照左右形状，平放在另一张纸上。
遇到困难：折痕明显可以夹在书页中压平，不使用热熨斗。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940002) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940002 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000251,940002,'普通纸','1–2 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940002) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000251);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000252,940002,'铅笔','1 支',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940002) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000252);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000253,940002,'圆头剪刀','1 把',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940002) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000253);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940002,940002,'纸','GUIDED','剪刀可能划伤；请坐稳操作，不熟悉工具时请带领者协助。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940002) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940002);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940003,@starter_owner,'纸条交织卡','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','背景参考：MassArt Art Museum：纸编原理（背景参考）。本练习是独立编写的入门活动，不代表相关机构认证。',1,20,'纸艺','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940003);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000301,940003,1,'准备纸条','从两张纸各剪 5 条约 2 厘米宽的纸条，长度约 15 厘米。
遇到困难：宽度不一致可保留，做成自由节奏的纹理。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940003) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940003 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000302,940003,2,'排好纵条','把 5 条纸并排放置，仅在顶端用一条胶带固定，留出小间隙。
遇到困难：容易移动时在底纸上固定，不直接粘贵重桌面。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940003) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940003 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000303,940003,3,'交替穿入','横条从第一条上方、第二条下方依次穿过；下一行反过来。
遇到困难：错一格时只抽出该横条，再沿上下一格重穿。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940003) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940003 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000304,940003,4,'收好边缘','轻推横条让间隙均匀，用少量胶带把外围固定在底纸上。
遇到困难：不要追求完全无缝；留白也能形成纹样。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940003) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940003 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000351,940003,'普通纸','1–2 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940003) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000351);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000352,940003,'纸胶带','少量',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940003) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000352);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000353,940003,'圆头剪刀','1 把',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940003) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000353);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940003,940003,'纸','GUIDED','剪刀可能划伤；请坐稳操作，不熟悉工具时请带领者协助。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940003) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940003);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940004,@starter_owner,'双色纸编书签','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','背景参考：MassArt Art Museum：纸编原理（背景参考）。本练习是独立编写的入门活动，不代表相关机构认证。',1,20,'纸艺','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940004);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000401,940004,1,'选两组颜色','准备 3 条长纸条和 6 条短纸条，宽约 1.5 厘米；旧纸可按深浅分组。
遇到困难：没有彩纸就用纸张正反面或不同笔迹的背面。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940004) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940004 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000402,940004,2,'固定一端','把长纸条并排，在顶端贴胶带，形成书签的纵向骨架。
遇到困难：太窄难操作时增加宽度，尺寸不必固定。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940004) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940004 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000403,940004,3,'穿出格纹','用短条上下交替穿过三条纵条，相邻行的起始方向相反。
遇到困难：先只完成三行，确认交错规律后再继续。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940004) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940004 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000404,940004,4,'贴到书签底纸','将织片贴到长方形底纸，多余边缘向背面折并固定。
遇到困难：厚度过大时仅用于标记封面，不硬夹精装书脊。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940004) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940004 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000451,940004,'普通纸','1–2 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940004) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000451);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000452,940004,'纸胶带','少量',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940004) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000452);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000453,940004,'圆头剪刀','1 把',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940004) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000453);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940004,940004,'纸','GUIDED','剪刀可能划伤；请坐稳操作，不熟悉工具时请带领者协助。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940004) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940004);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940005,@starter_owner,'三股线绳书签','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','本练习为织愈原创日常手作，不宣称属于特定非遗传承体系。',1,15,'编织','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940005);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000501,940005,1,'固定上端','三根线并排，上端打一个松结并贴在底纸上，尾部留约三指长。
遇到困难：打结困难可以直接用胶带固定三根线的端头。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940005) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940005 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000502,940005,2,'左线过中间','把左侧线移过中间线，放到两根线之间，轻轻拉直。
遇到困难：将线按颜色区分，纠缠时先平放分开。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940005) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940005 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000503,940005,3,'右线过中间','把右侧线移过现在的中间线；左右轮流，编到约手掌长度。
遇到困难：越编越紧时退回一两个交叉点，放松张力。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940005) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940005 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000504,940005,4,'收尾留穗','末端打结或贴胶带，保留少量散开的线尾，作为书签摆放。
遇到困难：仅做书签或桌面装饰，不作为佩戴或承重绳。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940005) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940005 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000551,940005,'预裁棉线','3 根各约 40 厘米',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940005) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000551);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000552,940005,'纸胶带','少量',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940005) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000552);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940005,940005,'线','LOW','线材不缠绕身体，不用于佩戴、承重或固定物品。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940005) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940005);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940006,@starter_owner,'绕线色块卡','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','本练习为织愈原创日常手作，不宣称属于特定非遗传承体系。',1,15,'编织','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940006);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000601,940006,1,'选一张底卡','选择手掌大小的平整卡纸，在背面贴住线头。
遇到困难：卡纸太软时叠两层，以免绕线后弯曲。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940006) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940006 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000602,940006,2,'绕第一组线','沿短边绕 5 圈，每圈留一点空隙，线贴住纸但不要勒紧。
遇到困难：卡纸弯曲时放松两圈；手指远离被拉紧的线。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940006) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940006 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000603,940006,3,'变化方向','在背面固定线，再换一个方向绕 5 圈，形成交叉的色块。
遇到困难：线不够就保留一组平行线，也是一件完成品。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940006) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940006 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000604,940006,4,'固定并摆放','最后线头贴在背面，把卡纸平放，观察线条的疏密。
遇到困难：松动的线头再补一小段胶带，不用胶枪。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940006) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940006 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000651,940006,'废卡纸','1 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940006) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000651);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000652,940006,'预裁棉线','约 1 米',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940006) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000652);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000653,940006,'纸胶带','少量',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940006) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000653);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940006,940006,'线','LOW','线材不缠绕身体，不用于佩戴、承重或固定物品。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940006) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940006);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940007,@starter_owner,'纸上针脚地图','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','本练习为织愈原创日常手作，不宣称属于特定非遗传承体系。',1,10,'纸艺','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940007);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000701,940007,1,'画一条路线','在纸上画一条缓缓转弯的细线，给两端留出空白。
遇到困难：路线歪斜无需擦掉，顺着它继续。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940007) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940007 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000702,940007,2,'加入短线','沿路线画短短的线段，每段之间留一点距离，像平针的节奏。
遇到困难：手累时把线段画长些，或先停下来。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940007) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940007 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000703,940007,3,'增加第二条','在旁边画一条不同疏密的路线，比较两种节奏。
遇到困难：只想保留一条也可以直接收尾。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940007) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940007 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000704,940007,4,'圈住喜欢的一段','在最喜欢的一段旁边画小点作标记，把纸收进本子。
遇到困难：不需要给作品打分，标记只是个人选择。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940007) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940007 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000751,940007,'普通纸','1–2 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940007) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000751);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000752,940007,'铅笔','1 支',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940007) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000752);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940007,940007,'纸','LOW','注意纸张边缘；使用笔时避免尖端朝向身体。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940007) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940007);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940008,@starter_owner,'撕纸山丘','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','本练习为织愈原创日常手作，不宣称属于特定非遗传承体系。',1,15,'纸艺','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940008);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000801,940008,1,'分成几张纸片','取两张纸，用手沿纸边撕出三张大小不同的纸片。
遇到困难：先折出浅痕再撕，更容易控制。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940008) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940008 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000802,940008,2,'撕出起伏','沿每张纸片一侧缓慢撕出高低起伏，作为山丘上缘。
遇到困难：裂口可以成为山谷，不用追求平滑边缘。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940008) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940008 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000803,940008,3,'由远到近摆放','先摆最高的一层，再叠较低的两层，留出各层边线。
遇到困难：层次看不清时拉开距离，或翻用纸张背面。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940008) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940008 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000804,940008,4,'固定画面','用少量胶带从背面固定纸片，放在底纸上完成风景。
遇到困难：没有胶带可先平铺拍照，再分类收好纸片。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940008) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940008 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000851,940008,'普通纸','1–2 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940008) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000851);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000852,940008,'纸胶带','少量',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940008) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000852);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940008,940008,'纸','LOW','注意纸张边缘；使用笔时避免尖端朝向身体。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940008) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940008);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940009,@starter_owner,'对折纹样卡','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','本练习为织愈原创日常手作，不宣称属于特定非遗传承体系。',1,10,'纸艺','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940009);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000901,940009,1,'建立中线','纸张对折再展开，折痕作为中线，留出上下边距。
遇到困难：不想折纸也可用笔轻画中线。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940009) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940009 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000902,940009,2,'画左侧纹样','在中线左边画三个圆点和两条弧线，保持图形简单。
遇到困难：从一个点开始就好，不必一次画满。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940009) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940009 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000903,940009,3,'回应右侧','在右边相近位置画对应图形；允许左右大小不同。
遇到困难：对称困难时只对应位置，不要求完全重合。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940009) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940009 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94000904,940009,4,'收尾留白','挑一个图形加深颜色，其余保持留白，把纸再次合拢。
遇到困难：图案太满可以用另一张纸做简单边框。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940009) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940009 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000951,940009,'普通纸','1–2 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940009) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000951);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94000952,940009,'铅笔','1 支',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940009) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94000952);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940009,940009,'纸','LOW','注意纸张边缘；使用笔时避免尖端朝向身体。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940009) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940009);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940010,@starter_owner,'四格纹理小册','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','本练习为织愈原创日常手作，不宣称属于特定非遗传承体系。',1,20,'纸艺','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940010);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001001,940010,1,'分成四格','纸张横向、纵向各对折一次，展开得到四个区域。
遇到困难：折痕不直可沿折痕自由画边界。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940010) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940010 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001002,940010,2,'点与线','第一格画疏落圆点，第二格画平行短线，保留空白。
遇到困难：线条过密时暂停，换到下一格即可。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940010) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940010 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001003,940010,3,'弧与格','第三格画弧线，第四格画交错线；每格只用一种节奏。
遇到困难：不必画满，半格纹理也足以比较。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940010) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940010 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001004,940010,4,'折成小册','沿原折痕合起，选一面作封面，用一个图形作标记。
遇到困难：较厚的纸只对折一次，不强压多层纸。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940010) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940010 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94001051,940010,'普通纸','1–2 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940010) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94001051);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94001052,940010,'铅笔','1 支',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940010) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94001052);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940010,940010,'纸','LOW','注意纸张边缘；使用笔时避免尖端朝向身体。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940010) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940010);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940011,@starter_owner,'波纹留白卡','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','本练习为织愈原创日常手作，不宣称属于特定非遗传承体系。',1,10,'纸艺','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940011);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001101,940011,1,'留出中心','在纸中间画一个小椭圆，周围留白。
遇到困难：椭圆不圆也没关系，把它作为独特起点。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940011) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940011 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001102,940011,2,'向外延伸','沿中心外围画三条弧线，尽量互不接触。
遇到困难：线条相碰时在交点画一个小点，不必擦掉。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940011) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940011 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001103,940011,3,'变化间隔','在一侧增加两条近些的弧线，另一侧保持宽间隔。
遇到困难：眼睛疲劳时抬头看远处，随时结束。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940011) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940011 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001104,940011,4,'保留空白','停止加线，看看纸上的空白，选择一个方向作为画面的上方。
遇到困难：留白不是未完成，不需要填满。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940011) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940011 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94001151,940011,'普通纸','1–2 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940011) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94001151);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94001152,940011,'铅笔','1 支',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940011) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94001152);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940011,940011,'纸','LOW','注意纸张边缘；使用笔时避免尖端朝向身体。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940011) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940011);
INSERT INTO craft_project(id,inheritor_id,title,intro,culture_bg,level,est_minutes,category,therapy_tags,status,create_time,update_time,deleted) SELECT 940012,@starter_owner,'纸带环环装饰','织愈原创入门练习 · 编辑估时 · 非传承人认证课程','本练习为织愈原创日常手作，不宣称属于特定非遗传承体系。',1,15,'纸艺','',1,NOW(),NOW(),0 WHERE @starter_owner IS NOT NULL AND NOT EXISTS(SELECT 1 FROM craft_project WHERE id=940012);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001201,940012,1,'准备宽纸带','准备 5 条约两指宽、手掌长的纸带，纸边保持平整。
遇到困难：纸带太短时改做更小的环。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940012) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940012 AND step_no=1);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001202,940012,2,'闭合第一个环','把纸带两端重叠，用胶带固定，形成第一个松松的圆环。
遇到困难：连接不牢时增加重叠长度，不用订书钉。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940012) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940012 AND step_no=2);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001203,940012,3,'逐个穿接','下一条先穿过前一环，再闭合端头，做出 5 环短链。
遇到困难：某个环开了可重新贴合，无需拆掉整条。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940012) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940012 AND step_no=3);
INSERT INTO craft_step(id,project_id,step_no,title,guide,create_time,update_time,deleted) SELECT 94001204,940012,4,'桌面摆放','把短链平放成弧形，观察交错关系，收起剩余纸片。
遇到困难：仅作桌面装饰，不套在身体上，不靠近火源。',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940012) AND NOT EXISTS(SELECT 1 FROM craft_step WHERE project_id=940012 AND step_no=4);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94001251,940012,'普通纸','1–2 张',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940012) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94001251);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94001252,940012,'纸胶带','少量',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940012) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94001252);
INSERT INTO craft_material(id,project_id,name,qty,create_time,update_time,deleted) SELECT 94001253,940012,'圆头剪刀','1 把',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940012) AND NOT EXISTS(SELECT 1 FROM craft_material WHERE id=94001253);
INSERT INTO craft_safety(id,project_id,medium,level,hazards,guidance,source,create_time,update_time,deleted) SELECT 940012,940012,'纸','GUIDED','剪刀可能划伤；请坐稳操作，不熟悉工具时请带领者协助。','桌面保持干燥整洁，手部不适时暂停；不用刀片、热胶枪或明火。','织愈编辑材料风险提示，未经专业安全认证',NOW(),NOW(),0 WHERE EXISTS(SELECT 1 FROM craft_project WHERE id=940012) AND NOT EXISTS(SELECT 1 FROM craft_safety WHERE project_id=940012);
COMMIT;

-- 原创纸艺为一般材料分类，不作非遗认证。
