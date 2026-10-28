-- ============================================================
-- 20261028_craft_recommend_event.sql
-- 目的：记录推荐卡片点击，为疗愈闭环漏斗提供「点击推荐」阶段。
--
-- 备份（执行前）：
--   mysqldump -u<user> -p <db> craft_recommendation craft_recommendation_item > backup_recommend_20261028.sql
--
-- 重复执行策略：
--   CREATE TABLE IF NOT EXISTS，可安全重复执行；已有表不会重复创建。
--   若需要新增索引，请先查 information_schema.STATISTICS 再执行 ALTER。
--
-- 回滚（确认无新数据依赖后）：
--   DROP TABLE craft_recommend_event;
--
-- 说明：
--   - event_type 当前只写入 CLICK；预留 EXPOSURE，但本轮不记录渲染曝光。
--   - 唯一键保证同一用户/推荐/项目/事件只记一次；服务端仍做幂等判断。
-- ============================================================

CREATE TABLE IF NOT EXISTS craft_recommend_event (
  id                BIGINT      NOT NULL,
  user_id           BIGINT      NOT NULL COMMENT '点击用户',
  recommendation_id BIGINT      NOT NULL COMMENT 'craft_recommendation.id',
  project_id        BIGINT      NOT NULL COMMENT '被点击项目',
  event_type        VARCHAR(16) NOT NULL DEFAULT 'CLICK' COMMENT 'CLICK；预留 EXPOSURE',
  source            VARCHAR(16) NOT NULL COMMENT 'HOME / RECOMMEND',
  create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recommend_event (user_id, recommendation_id, project_id, event_type),
  KEY idx_recommend_event_time (event_type, create_time),
  KEY idx_recommend_event_rec (recommendation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐点击事件表';
