-- ============================================================
-- 「织愈」字典：技艺品类 dict_category（7 条）
--   * 仅前端展示用，不参与推荐匹配
--   * craft_project.category 存本表的 label（中文品类名）
--   * 幂等：可重复执行
-- ============================================================

-- Windows 版客户端默认 gbk，必须先声明 utf8mb4，否则中文乱码
SET NAMES utf8mb4;

USE zhiyu;

INSERT INTO dict_category (id, code, label, sort_no, create_time, update_time, deleted) VALUES
(1, 'zharan',        '扎染',     1, NOW(), NOW(), 0),
(2, 'ganxiu',        '赣绣',     2, NOW(), NOW(), 0),
(3, 'jianzhi',       '剪纸',     3, NOW(), NOW(), 0),
(4, 'taoyi',         '陶艺',     4, NOW(), NOW(), 0),
(5, 'bianzhi',       '编织',     5, NOW(), NOW(), 0),
(6, 'zhuanke',       '篆刻',     6, NOW(), NOW(), 0),
(7, 'chuicaoyinhua', '捶草印花', 7, NOW(), NOW(), 0) AS new
ON DUPLICATE KEY UPDATE
  code        = new.code,
  label       = new.label,
  sort_no     = new.sort_no,
  update_time = new.update_time,
  deleted     = new.deleted;
