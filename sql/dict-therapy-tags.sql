-- ============================================================
-- 「织愈」字典：疗愈效果标签 dict_therapy_tags（12 条）
--   * 规范词表：craft_project.therapy_tags 与 dict_mood.therapy_tags 只能取本表的 label
--   * 不含技艺名（技艺名见 dict_category）、不含情绪词（情绪词见 dict_mood）
--   * 幂等：可重复执行
-- ============================================================

-- Windows 版客户端默认 gbk，必须先声明 utf8mb4，否则中文乱码
SET NAMES utf8mb4;

USE zhiyu;

INSERT INTO dict_therapy_tags (id, label, sort_no, create_time, update_time, deleted) VALUES
(1,  '专注',     1,  NOW(), NOW(), 0),
(2,  '放空',     2,  NOW(), NOW(), 0),
(3,  '释放',     3,  NOW(), NOW(), 0),
(4,  '成就感',   4,  NOW(), NOW(), 0),
(5,  '安抚',     5,  NOW(), NOW(), 0),
(6,  '陪伴',     6,  NOW(), NOW(), 0),
(7,  '静心',     7,  NOW(), NOW(), 0),
(8,  '创造表达', 8,  NOW(), NOW(), 0),
(9,  '轻量成就', 9,  NOW(), NOW(), 0),
(10, '情绪稳定', 10, NOW(), NOW(), 0),
(11, '触觉安抚', 11, NOW(), NOW(), 0),
(12, '物理宣泄', 12, NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  label       = new.label,
  sort_no     = new.sort_no,
  update_time = new.update_time,
  deleted     = new.deleted;
