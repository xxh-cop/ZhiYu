-- ============================================================
-- 「织愈」初始化数据
-- 注意：密码均为 BCrypt 哈希，演示用；生产环境请重新生成
-- ============================================================
USE zhiyu;

-- ---------- 内置账号 ----------
-- admin / admin123
-- inheritor01 / inherit123
-- counselor01 / couns123
-- student01 / stu12345
INSERT INTO user (id, username, password, nickname, role, status) VALUES
(1, 'admin',       '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Q1m1x8o1Fz2G9Y0Q3aQaQaQaQaQ', '平台管理员', 3, 1),
(2, 'inheritor01', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Q1m1x8o1Fz2G9Y0Q3aQaQaQaQaQ', '赣绣王老师', 1, 1),
(3, 'counselor01', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Q1m1x8o1Fz2G9Y0Q3aQaQaQaQaQ', '心理中心李老师', 2, 1),
(4, 'student01',   '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Q1m1x8o1Fz2G9Y0Q3aQaQaQaQaQ', '小织', 0, 1);

-- ---------- 传承人资料 ----------
INSERT INTO inheritor_profile (id, user_id, skill, intro, honor, verified) VALUES
(1, 2, '赣绣', '从事赣绣四十余年，擅长针脚细腻的丝线绣品。', '省级非遗代表性传承人', 1);

-- ---------- 手作项目（已上架示例） ----------
INSERT INTO craft_project (id, inheritor_id, title, intro, culture_bg, level, est_minutes, therapy_tags, cover_url, status) VALUES
(101, 2, '解压扎染方巾', '用扎染的自然晕染，把焦虑折进布里、染在手中。', '扎染是国家级非遗，通过捆扎、浸染形成不可预测的纹样。', 1, 45, '压力,放空,专注', '/static/covers/zharan.jpg', 1),
(102, 2, '赣绣小书签', '一针一线，在缓慢的穿引中把注意力拉回当下。', '赣绣是江西地方刺绣，针法细腻、色彩温润。', 1, 60, '焦虑,专注,情绪稳定', '/static/covers/ganxiu.jpg', 1),
(103, 2, '剪纸释压团花', '一刀一纸，把烦恼剪进对称的纹样里。', '剪纸在江西多地节庆中广泛使用，讲究对称与节奏。', 1, 30, '低落,发泄,成就感', '/static/covers/jianzhi.jpg', 1),
(104, 2, '陶艺捏塑杯垫', '双手与泥土对话，在揉捏中释放紧绷。', '陶艺手工塑形，触觉反馈强，适合情绪焦躁时使用。', 2, 50, '愤怒,紧绷,释放', '/static/covers/taoyi.jpg', 1);

-- ---------- 步骤（以扎染为例） ----------
INSERT INTO craft_step (id, project_id, step_no, title, guide, audio_url) VALUES
(1001, 101, 1, '折布与捆扎', '把白色方巾按你喜欢的方式折叠，用棉线紧紧扎紧。不必追求整齐，随心意即可。', '/static/audio/101-1.mp3'),
(1002, 101, 2, '调配染料', '把染料缓缓倒入水盆，观察颜色在水中晕开。', '/static/audio/101-2.mp3'),
(1003, 101, 3, '浸染', '把扎好的布浸入染料，静静等待 10 分钟。', '/static/audio/101-3.mp3'),
(1004, 101, 4, '拆开与晾晒', '拆开棉线，展开属于你的独一无二纹样。', '/static/audio/101-4.mp3');

-- ---------- 互助小组 ----------
INSERT INTO support_group (id, name, description, status) VALUES
(1, '考试周搭子', '一起熬过期末周的手作搭子们', 1),
(2, '夜猫子疗愈所', '晚上睡不着时来做点安静的手作', 1);
