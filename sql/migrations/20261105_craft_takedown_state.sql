-- 2026-11-05 分层下架状态机 + 步骤音频资产字段
--
-- 一、craft_project：下架从「终态删除式」升级为「分层状态迁移」
--   status 语义扩展为 0待审 1上架 2下架 3归档（status=3 为本次新增值，此前未被占用；
--   craft_session.status 的 1..4 是另一套会话语义，互不相干）。
--   合法迁移：1→2（带 takedown_kind）、2→3（归档，由 CraftProjectArchiveWorker 到期执行）、
--   1→3 仅限数据清理场景（SQL 手工执行，无应用入口）；2/3 不可回 1（AY1 终态口径不变，
--   恢复上线走新建送审）。
-- 二、craft_step：音频资产三字段（扩展 craft_step 而非独立资产表——音频与步骤 1:1，
--   独立表只会增加 join 成本而无复用收益）。
-- 三、数据修复：项目 101 扎染四步的 /static/audio/101-*.mp3 仓库无此文件（死链）→ 置 NULL
--   并标 asset_status='missing'；init-data.sql 源头已同步，避免重建再种死链。
--
-- 幂等：information_schema 检查后加列，UPDATE 均带守卫条件，重复执行安全。

USE zhiyu;
SET NAMES utf8mb4;

-- ── craft_project 新列 ──
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='craft_project' AND COLUMN_NAME='takedown_kind')=0,
 'ALTER TABLE `craft_project` ADD COLUMN `takedown_kind` VARCHAR(16) DEFAULT NULL COMMENT ''下架类型：compliance 合规 / operational 运营（2026-11-05 分层下架）'' AFTER `audit_reason`',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='craft_project' AND COLUMN_NAME='takedown_at')=0,
 'ALTER TABLE `craft_project` ADD COLUMN `takedown_at` DATETIME DEFAULT NULL COMMENT ''下架时间'' AFTER `takedown_kind`',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='craft_project' AND COLUMN_NAME='archive_at')=0,
 'ALTER TABLE `craft_project` ADD COLUMN `archive_at` DATETIME DEFAULT NULL COMMENT ''归档时间/截止：运营下架必填；合规下架窗口结束转归档时回填'' AFTER `takedown_at`',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='craft_project' AND COLUMN_NAME='export_until')=0,
 'ALTER TABLE `craft_project` ADD COLUMN `export_until` DATETIME DEFAULT NULL COMMENT ''私有导出/只读窗口截止'' AFTER `archive_at`',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='craft_project' AND COLUMN_NAME='notice_key')=0,
 'ALTER TABLE `craft_project` ADD COLUMN `notice_key` VARCHAR(64) DEFAULT NULL COMMENT ''提示语模板 key，对应 resources/notices/<key>.md'' AFTER `export_until`',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- status 注释同步新语义（重复执行无害）
ALTER TABLE `craft_project`
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0
  COMMENT '0待审 1上架 2下架 3归档（2/3 不可回 1，恢复上线走新建送审）';

-- 存量 AY1 终态下架（status=2 且无类型）：按合规下架口径补齐元数据（AY1 下架即时收授权，
-- 与合规行为一致）；导出窗口自迁移时刻起给默认 72 小时（与 zhiyu.takedown.compliance
-- .export-window-hours 默认值一致），窗口结束后由归档 Worker 走 2→3 正规路径转归档。
UPDATE `craft_project`
SET takedown_kind='compliance',
    takedown_at=COALESCE(update_time, NOW()),
    export_until=DATE_ADD(NOW(), INTERVAL 72 HOUR),
    notice_key='craft-takedown-compliance'
WHERE status=2 AND deleted=0 AND takedown_kind IS NULL;

-- ── craft_step 音频资产字段 ──
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='craft_step' AND COLUMN_NAME='license_status')=0,
 'ALTER TABLE `craft_step` ADD COLUMN `license_status` VARCHAR(16) NOT NULL DEFAULT ''none'' COMMENT ''音频授权：none/cc0/owned/licensed/pending_review'' AFTER `audio_url`',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='craft_step' AND COLUMN_NAME='attribution')=0,
 'ALTER TABLE `craft_step` ADD COLUMN `attribution` VARCHAR(300) DEFAULT NULL COMMENT ''音频署名信息（CC/授权来源）'' AFTER `license_status`',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='craft_step' AND COLUMN_NAME='asset_status')=0,
 'ALTER TABLE `craft_step` ADD COLUMN `asset_status` VARCHAR(16) DEFAULT NULL COMMENT ''音频资产状态：ready 可播 / missing 缺失 / disabled 停用；NULL=无音频资产'' AFTER `attribution`',
 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 空串音频视为无音频（历史脏数据归一化；空串不是 NULL，勿回填 ready）
UPDATE `craft_step` SET audio_url=NULL WHERE deleted=0 AND audio_url='';
-- 纠正：无音频却被回填/误标 ready 的行复位（含上一版的空串行）
UPDATE `craft_step` SET asset_status=NULL, license_status='none'
WHERE deleted=0 AND audio_url IS NULL AND asset_status='ready';

-- 存量回填：有音频引用的步骤先视为 ready（授权状态待内容方确认 → pending_review），
-- 启动对账（CraftAudioAssetReconciliation）会逐条核对私有存储，缺失即降级 missing。
UPDATE `craft_step`
SET asset_status='ready', license_status='pending_review'
WHERE deleted=0 AND audio_url IS NOT NULL AND audio_url<>'' AND asset_status IS NULL;

-- 死链修复：项目 101 扎染四步的 /static/audio/101-*.mp3 仓库无此文件 → 置 NULL 并标 missing。
UPDATE `craft_step`
SET audio_url=NULL, asset_status='missing', license_status='none'
WHERE deleted=0 AND audio_url LIKE '/static/audio/101-%.mp3';
