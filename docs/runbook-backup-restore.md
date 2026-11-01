# 备份与恢复演练手册（2026-11-01）

本手册用于**隔离测试库/演示环境**的备份、恢复与审计演练，不是生产灾备方案。
真实校园试点前，需由运维负责人补充定时备份、异地存储、保留周期、密钥托管与恢复 RTO/RPO。

## 1. 安全边界

- 只在隔离库或维护窗口执行；恢复前确认目标库不是正在被试用的库。
- 备份文件包含个人信息与加密字段，按敏感数据管理：不提交仓库、不上传公开网盘、不粘贴到聊天。
- `FIELD_ENCRYPT_SECRET`、`JWT_SECRET` 等环境变量与数据库备份分开保管；缺少原加密密钥时，恢复后的密文字段不可读。
- 本手册不覆盖媒体对象转储、生产 DNS/HTTPS 切换或跨机房复制。

## 2. 备份前自检

在管理端「运行健康」页或调用只读接口确认关键表与行数：

```bash
curl -s http://localhost:8080/api/admin/ops/health \
  -H "Authorization: Bearer $ADMIN_TOKEN"
curl -s http://localhost:8080/api/admin/ops/backup-manifest \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

记录返回的 `generatedAt`、`presentTables`、关键表行数，作为恢复后的核对基线。健康接口不会返回密钥或业务内容。

## 3. 数据库备份（示例）

```bash
mkdir -p .ai-tmp/backups
mysqldump --single-transaction --routines --triggers \
  -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASSWORD" zhiyu \
  > ".ai-tmp/backups/zhiyu-$(date +%Y%m%d-%H%M%S).sql"
```

同时确认以下非数据库资产有可恢复副本：

- `ZHIYU_UPLOAD_DIR` 媒体目录（私有存储，按授权访问）；
- `ZHIYU_RAG_STORE_PATH` 向量文件及其 `.version` sidecar；
- 运行环境的 `.env`/密钥（另行加密保管，不进入仓库）；
- `sql/migrations/` 的增量脚本版本。

## 4. 隔离库恢复演练

1. 新建隔离库（不要指向当前演示库）：

```sql
CREATE DATABASE zhiyu_restore_check CHARACTER SET utf8mb4;
```

2. 导入备份并执行必要增量迁移（以恢复目标版本为准）：

```bash
mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASSWORD" zhiyu_restore_check < backup.sql
```

3. 使用隔离库启动一次后端（示例）：

```bash
DB_URL='jdbc:mysql://localhost:3306/zhiyu_restore_check?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai' \
DB_USERNAME="$DB_USER" DB_PASSWORD="$DB_PASSWORD" \
FIELD_ENCRYPT_SECRET="$FIELD_ENCRYPT_SECRET" JWT_SECRET="$JWT_SECRET" \
mvn spring-boot:run
```

`SchemaCompatibilityCheck` 缺列会阻止启动并给出迁移修复路径；不要用重建空库替代迁移。

4. 恢复后核对：

- 再次调用 `/api/admin/ops/backup-manifest`，与备份前基线比较关键表行数；
- 用演示学生登录，打开一次历史情绪会话、非遗目录、短练习与练习会话；
- 用管理员打开运行健康、非遗目录版本与审计日志；
- 确认媒体权限、隐私同意与角色权限未被绕过；
- 恢复到出错前的快照或删除隔离库，记录演练时间、基线差异和失败原因。

## 5. 审计演练

1. 在管理端执行一次低风险操作（例如读取健康、刷新列表），确认 `admin_operation_log` 不因只读操作产生伪造记录。
2. 触发一次安全的 Mock AI 请求，确认 `ai_audit_log` 增加且 `raw_input` 仍为密文。
3. 核对 `/api/admin/ops/health` 的 `aiAudit24h`、`blocked24h` 与数据库计数一致。
4. 记录无法验证的项目（真实外部 AI、短信/热线、真实备份周期），不要用 Mock 或截图代替。

## 6. 验收记录模板

| 项目 | 记录 |
| --- | --- |
| 演练日期/执行人 | |
| 备份文件位置与校验和 | |
| 备份前 manifest 快照 | |
| 恢复目标库 | |
| 启动/结构检查结果 | |
| 登录与核心路径结果 | |
| 行数差异 | |
| 未验证项与后续负责人 | |

> 本手册中的健康接口只读，不提供自动备份或一键恢复；不得据健康页“UP”宣称已完成灾备验收。
