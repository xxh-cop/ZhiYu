-- ============================================================
-- 「织愈」本地开发数据库彻底重置脚本
--
-- 用途：把本机 zhiyu 库完全删除后重建并导入种子数据。
--       仅用于本地/演示环境，会丢失全部数据，切勿在生产执行。
--
-- 用法（二选一）：
--   1) 逐条执行：
--        mysql -u root -p < sql/reset.sql
--        mysql -u root -p < sql/schema.sql
--        mysql -u root -p zhiyu < sql/init-data.sql
--   2) 一条命令串起来（Windows PowerShell 示例）：
--        cmd /c "mysql -u root -p < sql\reset.sql && mysql -u root -p < sql\schema.sql && mysql -u root -p zhiyu < sql\init-data.sql"
--
-- 注意：日常增量初始化只需 schema.sql + init-data.sql（两者均幂等，可重复执行）。
-- ============================================================

DROP DATABASE IF EXISTS zhiyu;
CREATE DATABASE zhiyu DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 建表与数据请继续执行：
--   sql/schema.sql
--   sql/init-data.sql
