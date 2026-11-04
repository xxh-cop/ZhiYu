# dev-run.ps1 — 加载 .env 并启动织愈后端（S4 2026-11-04）
#
# 用途：Spring Boot 不读 .env。本脚本把仓库根目录 .env 的 KEY=VALUE 逐行
# 导出为进程环境变量后启动后端，使真实 AI provider（DEEPSEEK_* 等）生效，
# 用于真实 AI 验收（跑分、压测、联调），替代手工 export。
#
# 安全约束：
#   - 只打印「已加载 N 个变量」，绝不打印任何变量名或变量值。
#   - .env 永不提交（.gitignore 已覆盖）。
#
# 用法（在仓库任意目录调用均可，脚本自行定位根目录）：
#   powershell -File scripts/dev-run.ps1                # 默认 dev profile，端口 8080
#   powershell -File scripts/dev-run.ps1 -Port 8081     # 避让端口
#   powershell -File scripts/dev-run.ps1 -Profile test  # 切换 profile（一般不需要）
#   powershell -File scripts/dev-run.ps1 -DryRun        # 只加载 .env 并打印数量，不启动
#
# 判据（确认真实 provider 而非 Mock 降级）：
#   1. 启动日志出现「MockLlmClient 初始化完成（降级模式）」→ 仍是 Mock；
#      未出现该行且 AiClient 初始化完成 → 真实客户端。
#   2. 或登录管理员后调用 GET /api/admin/ai-governance/overview，
#      data.config.chatMode = "EXTERNAL" 为真实，"MOCK" 为降级。

param(
    [int]$Port = 8080,
    [string]$Profile = "dev",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

# ── 定位仓库根目录（脚本位于 <root>/scripts/）──
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
$backend = Join-Path $root "zhiyu-backend"

# ── 解析 .env：KEY=VALUE，跳过注释/空行；值可带成对引号；行内注释仅整行 # 生效 ──
$loaded = 0
if (Test-Path $envFile) {
    # 显式 UTF-8 读取（AGENTS.md §10：禁止依赖默认编码读仓库文件）
    $lines = [System.IO.File]::ReadAllLines($envFile, [System.Text.Encoding]::UTF8)
    foreach ($line in $lines) {
        $t = $line.Trim()
        if ($t.Length -eq 0 -or $t.StartsWith("#")) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $name = $t.Substring(0, $eq).Trim()
        $value = $t.Substring($eq + 1).Trim()
        # 允许 export KEY=VALUE 写法
        if ($name.StartsWith("export ")) { $name = $name.Substring(7).Trim() }
        if ($name -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") { continue }
        # 去掉成对的首尾单/双引号
        $isDoubleQuoted = $value.StartsWith('"') -and $value.EndsWith('"')
        $isSingleQuoted = $value.StartsWith("'") -and $value.EndsWith("'")
        if ($value.Length -ge 2 -and ($isDoubleQuoted -or $isSingleQuoted)) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        # 进程级环境变量（仅对随后启动的 mvn 及其子进程生效）
        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
        $loaded++
    }
    Write-Host ("已加载 {0} 个环境变量（.env）" -f $loaded)
} else {
    Write-Host "未找到 .env（$envFile），将以现有环境变量启动（AI 可能处于 Mock 降级）"
}

if ($DryRun) {
    Write-Host "DryRun：环境变量已导出到当前进程，未启动后端。"
    exit 0
}

# ── 启动后端（-D 参数全部作为独立数组元素传递，规避 PowerShell 解析 . 的坑）──
Push-Location $backend
try {
    $mvnArgs = @(
        "spring-boot:run",
        "-Dmaven.repo.local=../.ai-tmp/m2",
        "-Dspring-boot.run.profiles=$Profile",
        "-Dspring-boot.run.arguments=--server.port=$Port"
    )
    Write-Host "启动 zhiyu-backend（profile=$Profile, port=$Port）…"
    & mvn @mvnArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
