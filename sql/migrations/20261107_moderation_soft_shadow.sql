-- 软档影子模式观测台账（2026-11-07）：只统计「若开启软档会怎样」，绝不改变线上判定与可见性。
-- 设计口径：只 INSERT 本表；不写 work/work_comment 软档字段、不影响审核结论、不入抽检池、不发通知。
-- 幂等：information_schema 检查后执行，重复执行安全。开关 zhiyu.moderation.soft-tier.shadow.enabled 默认 false。
USE zhiyu;
SET NAMES utf8mb4;

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='moderation_soft_shadow')=0,
 'CREATE TABLE `moderation_soft_shadow` (
  `id`            BIGINT       NOT NULL,
  `target_type`   VARCHAR(10)  NOT NULL COMMENT ''work/comment'',
  `target_id`     BIGINT       NOT NULL COMMENT ''对象 id（雪花）'',
  `author_id`     BIGINT       NOT NULL COMMENT ''作者（灰度哈希分群与本人数据口径）'',
  `judge_status`  VARCHAR(12)  NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING 待重试/OK 已判定/UNAVAILABLE 终态不可用（达重试上限）'',
  `tendency`      VARCHAR(20)  DEFAULT NULL COMMENT ''若开启口径的判定结果（含窄门后；judge_status=OK 时有值）'',
  `narrative`     VARCHAR(20)  DEFAULT NULL COMMENT ''窄门 narrative 原始枚举值留证（仅枚举 token，非内容）'',
  `audit_log_id`  BIGINT       DEFAULT NULL COMMENT ''最近一次判定关联的 ai_audit_log（原文加密存那里，本表不存原文）'',
  `attempts`      INT          NOT NULL DEFAULT 0 COMMENT ''已尝试次数（熔断窗失败不一次定终态，重试至 max-attempts 才落 UNAVAILABLE）'',
  `last_attempt_at` DATETIME   DEFAULT NULL COMMENT ''最后尝试时间'',
  `target_create_time` DATETIME DEFAULT NULL COMMENT ''对象送审时间（统计时间窗口径）'',
  `create_time`   DATETIME     DEFAULT NULL,
  `update_time`   DATETIME     DEFAULT NULL,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shadow_target` (`target_type`,`target_id`),
  KEY `idx_shadow_target_ct` (`target_create_time`),
  KEY `idx_shadow_retry` (`judge_status`,`attempts`)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''软档影子模式观测台账（只观测不处置：不写业务软档字段/不影响审核结论/不入抽检池/不发通知）''','SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
