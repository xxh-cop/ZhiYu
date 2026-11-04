# 真实 AI 验收：.env 加载与判据（S4，2026-11-04）

## 背景

Spring Boot 不读 `.env`。此前真实 key 跑分/压测靠手工 export 环境变量，
学生端联调实例因未导出变量实际走了 MockLlmClient 降级而不自知。
本文件定义真实 AI 验收的标准步骤与「非 Mock」判据。

## 标准步骤

1. 确认仓库根目录 `.env` 存在（模板见 `.env.example`；`.env` 永不提交）。
2. 用启动脚本拉起后端（脚本读 `.env` → 导出进程环境变量 → 启动，只打印数量）：
   ```powershell
   powershell -File scripts/dev-run.ps1 -Port 8081
   ```
   - `-Port` 避让占用端口；`-DryRun` 只验证加载数量不启动。
   - 脚本只打印「已加载 N 个变量」，不打印任何变量名与值；终端日志、截图同样不得出现。
3. 按需执行验收（评测跑分 / SSE 压测 / 联调冒烟）。

## 「真实 provider 而非 Mock」判据（满足其一即可）

| 判据 | 真实（通过） | Mock 降级（不通过） |
|---|---|---|
| 启动日志 | 无「MockLlmClient 初始化完成（降级模式…）」行，且 AiClient 初始化完成 | 出现「MockLlmClient 初始化完成（降级模式：未配置 DEEPSEEK_API_KEY）」 |
| 接口验证 | `GET /api/admin/ai-governance/overview` 返回 `data.config.chatMode = "EXTERNAL"` | `chatMode = "MOCK"` |

接口验证示例（管理员 token 见各自环境，不在文档中记录）：

```powershell
$r = Invoke-RestMethod -Uri "http://localhost:8081/api/admin/ai-governance/overview" -Headers @{ Authorization = "Bearer <管理员token>" }
$r.data.config.chatMode   # 期望 EXTERNAL
```

## 注意事项

- 脚本通过进程级环境变量注入，不影响系统/用户级环境；关闭终端即失效。
- `-D` 参数已按数组元素传递（`"-Dmaven.repo.local=../.ai-tmp/m2"`），规避 PowerShell 把 `.` 解析为属性访问的坑（AGENTS.md §10）。
- 脚本文件为 UTF-8 with BOM（Windows PowerShell 5.1 无 BOM 会按 ANSI 误读中文注释与文案）。
- 生产/演示环境不依赖本脚本：由部署平台的环境变量机制注入，本脚本仅服务本地开发与验收。
- 验收报告必须注明判据结果（chatMode 或日志行），「启动了服务」不等于「真实 AI 生效」。
