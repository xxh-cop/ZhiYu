-- 2026-11-16
-- 修正 craft_project.inheritor_id：101/103/104 不再错挂赣绣账号。
-- 这里补建的是「平台示范」占位账号，nickname 明确标注、inheritor_profile.verified=0，
-- 不会被当成真实传承人，也不会出现在学生端示范者位置；等真实示范者入驻后再改成真实账号。
-- 同时补齐 101–104 的材料安全提示（平台编辑风险提示，不是专业安全认证）。

-- 平台示范占位账号：role=1 便于后台按传承人维度维护，status=0 禁止登录。
INSERT INTO user (id, username, password, nickname, role, status, create_time, update_time, deleted) VALUES
(910001, 'platform_demo_zaran', '$2a$10$dummyhash', '平台示范 · 扎染', 1, 0, NOW(), NOW(), 0),
(910002, 'platform_demo_paper', '$2a$10$dummyhash', '平台示范 · 剪纸', 1, 0, NOW(), NOW(), 0),
(910003, 'platform_demo_clay',  '$2a$10$dummyhash', '平台示范 · 陶艺', 1, 0, NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  nickname    = new.nickname,
  role        = new.role,
  status      = new.status,
  update_time = new.update_time,
  deleted     = new.deleted;

INSERT INTO inheritor_profile (id, user_id, skill, intro, honor, verified, create_time, update_time, deleted) VALUES
(9101, 910001, '扎染', '平台编辑示范账号；非传承人认证，示范内容待后台核验。', '平台示范（未认证）', 0, NOW(), NOW(), 0),
(9102, 910002, '剪纸', '平台编辑示范账号；非传承人认证，示范内容待后台核验。', '平台示范（未认证）', 0, NOW(), NOW(), 0),
(9103, 910003, '陶艺', '平台编辑示范账号；非传承人认证，示范内容待后台核验。', '平台示范（未认证）', 0, NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  skill       = new.skill,
  intro       = new.intro,
  honor       = new.honor,
  verified    = new.verified,
  update_time = new.update_time,
  deleted     = new.deleted;

-- 只改仍错挂赣绣账号的 3 个项目，避免覆盖后台后来的人工绑定。
UPDATE craft_project SET inheritor_id = 910001, update_time = NOW() WHERE id = 101 AND inheritor_id = 2 AND deleted = 0;
UPDATE craft_project SET inheritor_id = 910002, update_time = NOW() WHERE id = 103 AND inheritor_id = 2 AND deleted = 0;
UPDATE craft_project SET inheritor_id = 910003, update_time = NOW() WHERE id = 104 AND inheritor_id = 2 AND deleted = 0;

-- 101–104 安全标注：与 940001–940012 同一套「织愈编辑材料风险提示」口径。
INSERT INTO craft_safety (id, project_id, medium, level, hazards, guidance, source, create_time, update_time, deleted) VALUES
(9101, 101, '布·染料', 'GUIDED', '染料可能刺激皮肤或染色衣物；浸染时请戴手套、围裙，保持通风。', '使用预调染料，不接触眼口；皮肤敏感或破损时暂停；不加热、不使用明火。', '织愈编辑材料风险提示，未经专业安全认证', NOW(), NOW(), 0),
(9102, 102, '线·针', 'GUIDED', '绣针尖锐，可能扎伤；剪刀可能划伤。', '针不用时放入针插，不朝向他人；不熟悉工具时请带领者协助。', '织愈编辑材料风险提示，未经专业安全认证', NOW(), NOW(), 0),
(9103, 103, '纸·剪刀', 'GUIDED', '剪刀可能划伤；纸边可能割手。', '坐稳操作，剪刀尖端不对人；不熟悉工具时请带领者协助。', '织愈编辑材料风险提示，未经专业安全认证', NOW(), NOW(), 0),
(9104, 104, '陶泥', 'LOW', '陶泥粉尘可能刺激呼吸道；塑形工具尖端可能划伤。', '保持桌面湿润通风，不扬尘、不烧制；手部不适时暂停。', '织愈编辑材料风险提示，未经专业安全认证', NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  medium      = new.medium,
  level       = new.level,
  hazards     = new.hazards,
  guidance    = new.guidance,
  source      = new.source,
  update_time = new.update_time,
  deleted     = new.deleted;
