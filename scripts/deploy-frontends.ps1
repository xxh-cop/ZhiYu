# deploy-frontends.ps1 — 更换 API 地址后重建两个前端并覆盖发布
#
# 用途：API 地址变化时，一条命令完成「注入新地址 → 重建学生端 H5 与管理端
# → 校验产物 → 用 deployment 插件覆盖发布到原有两个站点」。
#
# 安全与约束：
#   - 不读取、不修改任何 .env；地址只通过构建期环境变量注入。
#   - API 地址默认 https://api.xiangjuntao.ltd（长期固定具名隧道），可用 -ApiBaseUrl 覆盖。
#   - 只构建、只发布，不回退或清理工作区未提交改动。
#
# 用法（在仓库根目录或任意目录）：
#   powershell -File scripts/deploy-frontends.ps1
#   powershell -File scripts/deploy-frontends.ps1 -ApiBaseUrl https://<其他地址> -AppSiteId <id> -AdminSiteId <id>
#
# 前置条件：
#   1. 已安装 Node.js 与 pnpm；两个工程的依赖已安装（zhiyu-app 用 npm，zhiyu-admin-vben 用 pnpm）。
#   2. Trae deployment 插件可用（脚本自动定位其 CLI 与静态适配脚本）。
#   3. API 地址可达；遇到 5xx / 连接失败会先等待重试，仍失败才退出。

param(
    [string]$ApiBaseUrl = "https://api.xiangjuntao.ltd",

    [string]$AppSiteId = "37lepx8drvjlbp6is2i",
    [string]$AdminSiteId = "37leqmgwx2l4gsn6y55"
)

$ErrorActionPreference = "Continue"

# ── 地址规范化 ──
$ApiBaseUrl = $ApiBaseUrl.Trim().TrimEnd('/')
if ($ApiBaseUrl -notmatch '^https?://') {
    throw "ApiBaseUrl 必须是完整 http(s) 地址：$ApiBaseUrl"
}
$adminApiUrl = "$ApiBaseUrl/api"
$root = Split-Path -Parent $PSScriptRoot

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host "== $Text ==" -ForegroundColor Cyan
}

# ── 1. 隧道可达性（5xx / 连接失败先等待重试；401/403/404 视为可达）──
Write-Step "检查隧道可达性"
$probeCode = ""
for ($attempt = 1; $attempt -le 6; $attempt++) {
    $probeCode = (& curl.exe -sS -o NUL -w "%{http_code}" --max-time 20 "$ApiBaseUrl/api") 2>$null
    if ($probeCode -and $probeCode -ne "000" -and $probeCode -notmatch '^5') { break }
    if ($attempt -lt 6) {
        Write-Host "探测到 HTTP $probeCode，等待 10 秒后重试（$attempt/6）..."
        Start-Sleep -Seconds 10
    }
}
if (-not $probeCode -or $probeCode -eq "000" -or $probeCode -match '^5') {
    throw "隧道不可达（HTTP $probeCode）：$ApiBaseUrl/api。请先恢复隧道后重试。"
}
Write-Host "隧道可达：HTTP $probeCode" -ForegroundColor Green

# ── 2. 定位 deployment 插件（取最高版本）──
Write-Step "定位 deployment 插件"
$pluginRoot = Join-Path $env:USERPROFILE ".trae-cn\plugins\trae-remote-official\deployment"
if (-not (Test-Path $pluginRoot)) {
    throw "未找到 deployment 插件目录：$pluginRoot"
}
$skillDir = Get-ChildItem $pluginRoot -Directory |
    Sort-Object { try { [version]$_.Name } catch { [version]"0.0.0" } } |
    Select-Object -Last 1 |
    ForEach-Object { Join-Path $_.FullName "skills\deployment" }
$cliPath = Join-Path $skillDir "bin\win32-x64\trae-deploy.exe"
$adapterPath = Join-Path $skillDir "scripts\prepare-static-function.mjs"
if (-not (Test-Path $cliPath) -or -not (Test-Path $adapterPath)) {
    throw "deployment 插件 CLI 或适配脚本缺失：$skillDir"
}
Write-Host "插件目录：$skillDir"

function Invoke-CliJson {
    param([string[]]$Arguments)
    # 参数中不含空格；路径位于系统临时目录且无空格
    # cmd /s /c 约定：整体再包一层引号，由 /s 去掉首尾引号后交给 cmd 解析
    $commandLine = '""' + $script:cliPath + '" ' + ($Arguments -join ' ') + '"'
    $stdout = & cmd.exe /d /s /c $commandLine 2>$null
    $exitCode = $LASTEXITCODE
    $text = ($stdout | Out-String).Trim()
    if ($exitCode -ne 0) {
        throw "trae-deploy 调用失败（退出码 $exitCode）：$($Arguments[0])`n$text"
    }
    return $text | ConvertFrom-Json
}

# ── 3. 构建 ──
Write-Step "构建学生端 H5（VITE_API_BASE_URL=$ApiBaseUrl）"
$env:VITE_API_BASE_URL = $ApiBaseUrl
Push-Location (Join-Path $root "zhiyu-app")
try {
    & npm run build:h5
    if ($LASTEXITCODE -ne 0) { throw "学生端构建失败" }
} finally {
    Pop-Location
}
$appDist = Join-Path $root "zhiyu-app\dist\build\h5"
if (-not (Test-Path (Join-Path $appDist "index.html"))) {
    throw "学生端产物缺少 index.html：$appDist"
}

Write-Step "构建管理端（VITE_GLOB_API_URL=$adminApiUrl）"
$env:VITE_GLOB_API_URL = $adminApiUrl
Push-Location (Join-Path $root "zhiyu-admin-vben")
try {
    & pnpm --filter @vben/web-ele build
    if ($LASTEXITCODE -ne 0) { throw "管理端构建失败" }
} finally {
    Pop-Location
}
$adminDist = Join-Path $root "zhiyu-admin-vben\apps\web-ele\dist"
if (-not (Test-Path (Join-Path $adminDist "index.html"))) {
    throw "管理端产物缺少 index.html：$adminDist"
}

# ── 4. 产物校验：新地址存在、旧本地地址与相对 /api 不存在 ──
function Assert-Dist {
    param(
        [string]$DistPath,
        [string[]]$Forbidden,
        [string]$Required
    )
    $requiredHit = $false
    $files = Get-ChildItem $DistPath -Recurse -File -Include *.js, *.html, *.css
    foreach ($file in $files) {
        $text = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        foreach ($bad in $Forbidden) {
            if ($text.Contains($bad)) {
                throw "产物仍包含 [$bad]：$($file.FullName)"
            }
        }
        if ($text.Contains($Required)) { $requiredHit = $true }
    }
    if (-not $requiredHit) {
        throw "产物未找到新接口地址 [$Required]：$DistPath"
    }
}

Write-Step "校验学生端产物"
Assert-Dist -DistPath $appDist -Forbidden @("localhost:8080", ".serveousercontent.com", ".lhr.life") -Required $ApiBaseUrl

Write-Step "校验管理端产物"
Assert-Dist -DistPath $adminDist -Forbidden @("localhost:8080", ".serveousercontent.com", ".lhr.life", '"/api"', "'/api'", '`/api`', "/api/files/") -Required $adminApiUrl

# ── 5. 覆盖发布 ──
function Invoke-Deploy {
    param(
        [string]$SiteId,
        [string]$DistPath,
        [string]$Title,
        [string]$Description,
        [string]$AppTags
    )
    $sourceDir = Join-Path ([System.IO.Path]::GetTempPath()) ("trae-deploy-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path (Join-Path $sourceDir "public") -Force | Out-Null
    try {
        Copy-Item -Path (Join-Path $DistPath "*") -Destination (Join-Path $sourceDir "public") -Recurse -Force
        $prepared = & node $script:adapterPath --source-dir $sourceDir
        if ($LASTEXITCODE -ne 0) { throw "静态适配失败：$prepared" }
        $adapterResult = ($prepared | Out-String).Trim() | ConvertFrom-Json
        $preparedDir = $adapterResult.data.source_dir
        $executionConfig = $adapterResult.data.execution_config

        $created = Invoke-CliJson @(
            "deployments", "create",
            "--site-id", $SiteId,
            "--title", $Title,
            "--description", $Description,
            "--type", "function-web",
            "--source-dir", $preparedDir,
            "--execution-config", $executionConfig,
            "--app-tags", $AppTags
        )
        $deploymentId = $created.data.deployment_id
        Write-Host "已创建 Deployment：$deploymentId，等待完成..."

        $waited = Invoke-CliJson @(
            "deployments", "wait",
            "--deployment-id", $deploymentId,
            "--timeout-seconds", "600"
        )
        if ($waited.data.deployment.status -ne 9) {
            throw "Deployment 未成功（status=$($waited.data.deployment.status)），请查看 logs。"
        }

        $site = Invoke-CliJson @("sites", "get", "--site-id", $SiteId)
        $hostname = $site.data.site_config.hostname
        Write-Host "站点已发布：https://$hostname" -ForegroundColor Green
        return "https://$hostname"
    } finally {
        # 清理本次临时目录（适配器目录 + 拷贝目录）
        Get-ChildItem ([System.IO.Path]::GetTempPath()) -Directory -Filter "trae-static-function-*" -ErrorAction SilentlyContinue |
            Where-Object { $_.LastWriteTime -gt (Get-Date).AddMinutes(-30) } |
            Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
        Remove-Item -Recurse -Force $sourceDir -ErrorAction SilentlyContinue
    }
}

Write-Step "覆盖发布学生端"
$appUrl = Invoke-Deploy -SiteId $AppSiteId -DistPath $appDist `
    -Title "redeploy-app" -Description "重新构建并覆盖发布学生端H5到现有站点" -AppTags "101,201,223,267"

Write-Step "覆盖发布管理端"
$adminUrl = Invoke-Deploy -SiteId $AdminSiteId -DistPath $adminDist `
    -Title "redeploy-admin" -Description "重新构建并覆盖发布管理端到现有站点" -AppTags "123,223"

Write-Step "完成"
Write-Host "学生端：$appUrl"
Write-Host "管理端：$adminUrl"
Write-Host "接口地址：$ApiBaseUrl（管理端 $adminApiUrl）"
