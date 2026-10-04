# 织愈后端启动脚本
# 自动读取仓库根的 .env 文件并注入环境变量
# 用法：在仓库根执行 .\start-backend.ps1

$ErrorActionPreference = 'Stop'

# 检查 .env 是否存在
$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path $envFile)) {
    Write-Host "❌ 未找到 .env 文件。请先复制 .env.example 为 .env 并填入真实值。" -ForegroundColor Red
    exit 1
}

# 白名单：.env 里只应放这些变量
$allowedKeys = @('DEEPSEEK_API_KEY', 'DEEPSEEK_BASE_URL', 'DEEPSEEK_CHAT_MODEL')
$envKeys = @()

# 逐行读取 .env，跳过注释和空行
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line -split '=', 2
        if ($parts.Length -eq 2) {
            $key = $parts[0].Trim()
            $value = $parts[1].Trim()
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
            Write-Host "  ✓ 加载 $key" -ForegroundColor Gray
            $envKeys += $key
        }
    }
}

# 检查非白名单变量
$warned = $false
foreach ($k in $envKeys) {
    if ($k -notin $allowedKeys) {
        if (-not $warned) {
            Write-Host "`n⚠️  .env 中检测到非白名单变量，可能覆盖 application-dev.yml 默认值：" -ForegroundColor Yellow
            $warned = $true
        }
        Write-Host "     - $k" -ForegroundColor Yellow
    }
}
if ($warned) {
    Write-Host "   建议：.env 只放 DEEPSEEK_* 变量，其他走 application-dev.yml 默认值。`n" -ForegroundColor Yellow
}

# 检查关键变量
if (-not $env:DEEPSEEK_API_KEY -or $env:DEEPSEEK_API_KEY -eq 'sk-your-real-key-here') {
    Write-Host "⚠️  DEEPSEEK_API_KEY 未配置或仍为占位符，后端将使用 MockLlmClient" -ForegroundColor Yellow
}

# 启动后端
$env:JAVA_HOME = 'D:\Java\jdk-17'
Set-Location (Join-Path $PSScriptRoot 'zhiyu-backend')

Write-Host "`n🚀 启动后端..." -ForegroundColor Cyan
mvn spring-boot:run "-Dmaven.repo.local=../.ai-tmp/m2"
