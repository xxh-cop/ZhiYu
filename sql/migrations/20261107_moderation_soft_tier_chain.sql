-- 审核软档处置链（2026-11-07）：软档台账/抽检池新表 + work/work_comment 降权热路径列。
-- 设计：docs/moderation-soft-tier-disposition-chain-20261107.md
-- 幂等：information_schema 检查后执行，重复执行安全。处置链开关默认 false，结构先行不影响线上行为。
USE zhiyu;
SET NAMES utf8mb4;

-- work / work_comment：软档降权截止（热路径读列，台账 moderation_soft_review.demote_until 为事实源）
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='work' AND COLUMN_NAME='soft_demote_until')=0,
 'ALTER TABLE `work` ADD COLUMN `soft_demote_until` DATETIME DEFAULT NULL COMMENT ''软档降权截止（2026-11-07 处置链；NULL=不降权；处置链开关关闭时读侧忽略本列）''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='work_comment' AND COLUMN_NAME='soft_demote_until')=0,
 'ALTER TABLE `work_comment` ADD COLUMN `soft_demote_until` DATETIME DEFAULT NULL COMMENT ''软档降权截止（2026-11-07 处置链；NULL=不降权；处置链开关关闭时读侧忽略本列）''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 软档台账 + 抽检池（一体）：所有软档落台账，sampled=1 进人工复核
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='moderation_soft_review')=0,
 'CREATE TABLE `moderation_soft_review` (
  `id`            BIGINT       NOT NULL,
  `target_type`   VARCHAR(10)  NOT NULL COMMENT ''work/comment'',
  `target_id`     BIGINT       NOT NULL COMMENT ''对象 id（雪花）'',
  `author_id`     BIGINT       NOT NULL COMMENT ''作者（作者分层抽样与本人数据口径）'',
  `ai_tendency`   VARCHAR(20)  NOT NULL DEFAULT ''SOFT_CONCERN'' COMMENT ''AI 建议倾向（当前固定软档）'',
  `narrative`     VARCHAR(20)  DEFAULT NULL COMMENT ''窄门 narrative 值留证（self_disclosure）'',
  `audit_log_id`  BIGINT       DEFAULT NULL COMMENT ''关联 ai_audit_log 建议记录，不重复存原文'',
  `sampled`       TINYINT      NOT NULL DEFAULT 0 COMMENT ''0未抽中 1抽中进人工池'',
  `sample_layer`  VARCHAR(30)  DEFAULT NULL COMMENT ''抽样分层：mandatory_lexicon/mandatory_riskpoint/mandatory_author_first/random/overflow_*'',
  `pool_status`   VARCHAR(20)  NOT NULL DEFAULT ''NOT_SAMPLED'' COMMENT ''NOT_SAMPLED/PENDING/ESCALATED/CLEAR/UPHOLD/BLOCK/EXPIRED'',
  `reviewer_id`   BIGINT       DEFAULT NULL COMMENT ''裁决人（管理员）'',
  `reason`        VARCHAR(255) DEFAULT NULL COMMENT ''裁决理由（BLOCK 必填）'',
  `sla_due_at`    DATETIME     DEFAULT NULL COMMENT ''复核时限（仅 sampled=1；入池 + sla-hours）'',
  `reviewed_at`   DATETIME     DEFAULT NULL COMMENT ''裁决时间'',
  `demote_until`  DATETIME     DEFAULT NULL COMMENT ''降权截止（台账侧事实源）'',
  `appeal_status` VARCHAR(20)  DEFAULT NULL COMMENT ''NONE/FILED/RESOLVED'',
  `appeal_reason` VARCHAR(200) DEFAULT NULL COMMENT ''申诉理由（必填过输入侧安全过滤后落库）'',
  `notified`      TINYINT      NOT NULL DEFAULT 0 COMMENT ''降权通知是否已发（幂等）'',
  `create_time`   DATETIME     DEFAULT NULL,
  `update_time`   DATETIME     DEFAULT NULL,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_soft_target` (`target_type`,`target_id`),
  KEY `idx_pool_sla` (`pool_status`,`sla_due_at`),
  KEY `idx_soft_author` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''软档处置台账/抽检池（处置链开关默认 false）''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 申诉理由（同日补丁：已建表库补齐；与 CREATE 同列定义，重复执行安全）
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='moderation_soft_review' AND COLUMN_NAME='appeal_reason')=0,
 'ALTER TABLE `moderation_soft_review` ADD COLUMN `appeal_reason` VARCHAR(200) DEFAULT NULL COMMENT ''申诉理由（必填过输入侧安全过滤后落库）''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
