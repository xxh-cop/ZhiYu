-- 2026-11-16
-- 用 AI 生成的演示数据补全 101/103/104 的示范者档案、104 的步骤与 101–104 的材料清单。
-- 所有演示身份在 nickname / honor / intro 中明确标注，不使用真实传承人姓名或认证。

UPDATE user
SET nickname = '青禾（演示示范）', school = '织愈内容实验室（演示）', update_time = NOW()
WHERE id = 910001 AND deleted = 0;
UPDATE user
SET nickname = '剪云（演示示范）', school = '织愈内容实验室（演示）', update_time = NOW()
WHERE id = 910002 AND deleted = 0;
UPDATE user
SET nickname = '陶然（演示示范）', school = '织愈内容实验室（演示）', update_time = NOW()
WHERE id = 910003 AND deleted = 0;

UPDATE inheritor_profile
SET intro = '平台邀约的扎染演示示范者（AI 演示档案）。擅长蓝染、捆扎与方巾纹样设计。',
    honor = '平台演示示范 · 非真实传承人认证',
    verified = 1,
    update_time = NOW()
WHERE id = 9101 AND deleted = 0;
UPDATE inheritor_profile
SET intro = '平台邀约的剪纸演示示范者（AI 演示档案）。擅长团花、对称纹样与安全剪法。',
    honor = '平台演示示范 · 非真实传承人认证',
    verified = 1,
    update_time = NOW()
WHERE id = 9102 AND deleted = 0;
UPDATE inheritor_profile
SET intro = '平台邀约的陶艺演示示范者（AI 演示档案）。擅长免烧陶泥捏塑与触觉引导。',
    honor = '平台演示示范 · 非真实传承人认证',
    verified = 1,
    update_time = NOW()
WHERE id = 9103 AND deleted = 0;

INSERT INTO craft_step (id, project_id, step_no, title, guide, audio_url, license_status, attribution, asset_status, create_time, update_time, deleted) VALUES
(4001, 104, 1, '揉泥醒泥', '取一块免烧陶泥，用手掌反复揉压，让泥料变得柔软均匀。', NULL, 'none', NULL, NULL, NOW(), NOW(), 0),
(4002, 104, 2, '压片成形', '把陶泥压成约一厘米厚的圆片，边缘用手指慢慢收圆。', NULL, 'none', NULL, NULL, NOW(), NOW(), 0),
(4003, 104, 3, '压纹修边', '用塑形工具压出浅纹，修掉多余的泥边；不必追求完全对称。', NULL, 'none', NULL, NULL, NOW(), NOW(), 0),
(4004, 104, 4, '阴干养护', '放在通风处自然阴干，不烧制；完全干透后再使用。', NULL, 'none', NULL, NULL, NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  project_id  = new.project_id,
  step_no     = new.step_no,
  title       = new.title,
  guide       = new.guide,
  audio_url   = new.audio_url,
  license_status = new.license_status,
  attribution = new.attribution,
  asset_status = new.asset_status,
  update_time = new.update_time,
  deleted     = new.deleted;

INSERT INTO craft_material (id, project_id, name, qty, create_time, update_time, deleted) VALUES
(10151, 101, '白色棉方巾', '1 块', NOW(), NOW(), 0),
(10152, 101, '植物靛蓝染料', '1 份', NOW(), NOW(), 0),
(10153, 101, '棉线', '1 卷', NOW(), NOW(), 0),
(10154, 101, '橡皮筋', '若干', NOW(), NOW(), 0),
(10155, 101, '一次性手套', '1 副', NOW(), NOW(), 0),
(10156, 101, '围裙', '1 件', NOW(), NOW(), 0),
(10251, 102, '素色棉布书签', '1 枚', NOW(), NOW(), 0),
(10252, 102, '绣线', '3 色', NOW(), NOW(), 0),
(10253, 102, '绣针', '1 枚', NOW(), NOW(), 0),
(10254, 102, '小绷架', '1 个', NOW(), NOW(), 0),
(10255, 102, '小剪刀', '1 把', NOW(), NOW(), 0),
(10351, 103, '正方形红纸', '3 张', NOW(), NOW(), 0),
(10352, 103, '安全剪刀', '1 把', NOW(), NOW(), 0),
(10353, 103, '铅笔', '1 支', NOW(), NOW(), 0),
(10354, 103, '橡皮', '1 块', NOW(), NOW(), 0),
(10355, 103, '折痕板', '1 块', NOW(), NOW(), 0),
(10451, 104, '免烧陶泥', '1 份', NOW(), NOW(), 0),
(10452, 104, '塑形刀', '1 把', NOW(), NOW(), 0),
(10453, 104, '擀泥杖', '1 根', NOW(), NOW(), 0),
(10454, 104, '湿布', '1 块', NOW(), NOW(), 0),
(10455, 104, '垫板', '1 块', NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  project_id  = new.project_id,
  name        = new.name,
  qty         = new.qty,
  update_time = new.update_time,
  deleted     = new.deleted;
