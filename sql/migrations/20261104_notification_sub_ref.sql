-- S2 评论通知 ref 语义改造（2026-11-04）。
-- ref_id 指向「跳转目标」（评论所属作品 work_id），新增 sub_ref_id 存「锚点」（评论 id）。
-- 幂等：information_schema 检查后加列；回填仅处理 sub_ref_id IS NULL 的行，重复执行安全。
USE zhiyu;
SET NAMES utf8mb4;

-- 1) 新增 sub_ref_id 列
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notification' AND COLUMN_NAME='sub_ref_id')=0,
 'ALTER TABLE `notification` ADD COLUMN `sub_ref_id` BIGINT DEFAULT NULL COMMENT ''二级关联对象 id（如评论 id 锚点）；ref_id 指向跳转目标（如所属作品）'' AFTER `ref_id`','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 历史 COMMENT_AUDIT 回填：ref_id=评论所属 work_id，sub_ref_id=原评论 id
--    仅处理能 JOIN 到 work_comment 的行；评论已物理缺失的历史行保持原样
--    （sub_ref_id 仍为 NULL，前端按旧语义不跳转），重复执行不会重复改写。
--    唯一索引 uk_notification_dedupe(user_id,type,ref_type,ref_id,dedupe_key) 不会冲突：
--    dedupe_key 形如 comment:<评论id>:<status>，按评论唯一，回填后元组仍唯一。
UPDATE notification n
JOIN work_comment c ON c.id = n.ref_id
SET n.sub_ref_id = n.ref_id,
    n.ref_id = c.work_id
WHERE n.type = 'COMMENT_AUDIT'
  AND n.ref_type = 'comment'
  AND n.sub_ref_id IS NULL;
