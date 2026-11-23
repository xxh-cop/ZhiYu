<#
.SYNOPSIS
    织愈本机演示一键启动：后端（按需）+ Cloudflare 具名隧道 zhiyu-demo + 公网自检。

.DESCRIPTION
    固定使用具名隧道 zhiyu-demo（api.xiangjuntao.ltd），不再使用免费随机隧道。

    行为约定：
      * 只检测 MySQL 3306 / Redis 6379，不在监听就报错退出，不代用户启动；
      * 端口空闲才启动后端 jar，并注入前端跨域白名单；端口被占用则识别后复用，不杀进程、不抢端口；
      * 同名隧道进程已在跑则复用，不重复启动；
      * 公网自检 https://api.xiangjuntao.ltd/api/crisis/hotlines，要求 HTTP 200 且 body 含 "code":0；
      * 只把自己拉起的 PID 写入 .ai-tmp/demo-stack-state.json，供 stop-demo.ps1 使用；
      * 所有日志写入 .ai-tmp/。

    不修改 .env、application*.yml，也不改动任何现有启动方式。

.PARAMETER Port
    后端端口，默认 8080。具名隧道配置固定转发 127.0.0.1:8080；
    用其他端口仅用于本地验证启停逻辑（此时跳过公网自检）。

.PARAMETER PublicUrl
    公网地址，默认 https://api.xiangjuntao.ltd。

.PARAMETER CorsAllowedOrigins
    逗号分隔的前端 Origin 白名单，默认两个已部署站点 + 本地开发地址。

.PARAMETER CloudflaredExe
    cloudflared.exe 路径，默认 C:\Program Files (x86)\cloudflared\cloudflared.exe。

.PARAMETER TunnelConfig
    具名隧道配置，默认 C:\Users\<当前用户>\.cloudflared\config.yml。

.PARAMETER TunnelName
    隧道名称，默认 zhiyu-demo。

.PARAMETER JavaExe / JarPath
    java 与后端 jar 路径，默认自动解析。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\zhiyu-backend\scripts\start-demo.ps1

.EXAMPLE
    # 仅验证启停逻辑，不动 8080 现有后端与隧道
    powershell -NoProfile -ExecutionPolicy Bypass -File .\zhiyu-backend\scripts\start-demo.ps1 -Port 8081
#>
[CmdletBinding()]
param(
    [int]$Port = 8080,
    [string]$PublicUrl = 'https://api.xiangjuntao.ltd',
    [string]$CorsAllowedOrigins = '',
    [string]$CloudflaredExe = '',
    [string]$TunnelConfig = '',
    [string]$TunnelName = 'zhiyu-demo',
    [string]$JavaExe = '',
    [string]$JarPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $backendRoot
$stateDir = Join-Path $repoRoot '.ai-tmp'
if (-not (Test-Path $stateDir)) {
    New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
}
$stateFile = Join-Path $stateDir 'demo-stack-state.json'
$backendLog = Join-Path $stateDir "demo-backend-$Port.log"
$tunnelLog = Join-Path $stateDir "demo-cloudflared-$Port.log"

function Get-EnvOrEmpty([string]$name) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ($null -eq $value) { return '' }
    return $value.Trim()
}

function Test-PortListening([int]$port) {
    $conn = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    return ($null -ne $conn)
}

function Get-ListeningPid([int]$port) {
    $conn = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($conn) { return [int]$conn.OwningProcess }
    return 0
}

function Test-HttpOk([string]$url, [int]$timeoutSeconds) {
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec $timeoutSeconds
        return ($resp.StatusCode -eq 200)
    } catch {
        return $false
    }
}

function Get-ChildProcessId([int]$parentPid, [string]$name) {
    for ($i = 0; $i -lt 12; $i++) {
        $child = Get-CimInstance Win32_Process -Filter "ParentProcessId=$parentPid" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -eq $name } |
            Select-Object -First 1
        if ($child) { return [int]$child.ProcessId }
        Start-Sleep -Milliseconds 500
    }
    return 0
}

function Start-DetachedPowerShell([string]$innerCommand, [string]$workingDirectory) {
    $cmdLine = 'powershell.exe -NoProfile -WindowStyle Hidden -Command "' + $innerCommand + '"'
    $result = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
        CommandLine      = $cmdLine
        CurrentDirectory = $workingDirectory
    }
    if ($result.ReturnValue -ne 0) {
        throw "创建后台进程失败（Win32_Process.Create 返回 $($result.ReturnValue)）"
    }
    return [int]$result.ProcessId
}

# ---------- 参数默认值 ----------
if (-not $CorsAllowedOrigins) {
    $CorsAllowedOrigins = Get-EnvOrEmpty 'ZHIYU_CORS_ALLOWED_ORIGINS'
    if (-not $CorsAllowedOrigins) {
        $CorsAllowedOrigins = 'http://localhost:*,http://127.0.0.1:*,https://37lepx8drvjlbp6is2i.traesite.cn,https://37leqmgwx2l4gsn6y55.traesite.cn'
    }
}

if (-not $CloudflaredExe) { $CloudflaredExe = Get-EnvOrEmpty 'ZHIYU_CLOUDFLARED_EXE' }
if (-not $CloudflaredExe) { $CloudflaredExe = 'C:\Program Files (x86)\cloudflared\cloudflared.exe' }
if (-not (Test-Path $CloudflaredExe)) {
    throw "未找到 cloudflared：$CloudflaredExe（先执行 winget install Cloudflare.cloudflared）"
}

if (-not $TunnelConfig) { $TunnelConfig = Join-Path (Join-Path $env:USERPROFILE '.cloudflared') 'config.yml' }
if (-not (Test-Path $TunnelConfig)) {
    throw "未找到具名隧道配置：$TunnelConfig（需先 cloudflared tunnel login 并创建 $TunnelName）"
}

if (-not $JavaExe) { $JavaExe = Get-EnvOrEmpty 'ZHIYU_JAVA_EXE' }
if (-not $JavaExe) {
    $javaHome = Get-EnvOrEmpty 'JAVA_HOME'
    if ($javaHome) {
        $candidate = Join-Path $javaHome 'bin\java.exe'
        if (Test-Path $candidate) { $JavaExe = $candidate }
    }
}
if (-not $JavaExe) {
    $javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCmd) { $JavaExe = $javaCmd.Source }
}
if (-not $JavaExe -or -not (Test-Path $JavaExe)) {
    throw '未找到 java.exe。请安装 JDK 17，或设置 JAVA_HOME / -JavaExe。'
}

if (-not $JarPath) { $JarPath = Get-EnvOrEmpty 'ZHIYU_DEMO_JAR' }
if (-not $JarPath) { $JarPath = Join-Path $backendRoot 'target\zhiyu-backend-0.1.0-SNAPSHOT.jar' }
if (-not (Test-Path $JarPath)) {
    $latest = Get-ChildItem (Join-Path $backendRoot 'target') -Filter '*.jar' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($latest) { $JarPath = $latest.FullName }
}
if (-not (Test-Path $JarPath)) {
    throw '未找到后端 jar。请先在 zhiyu-backend 执行：mvn package -DskipTests'
}
$jarMarker = Split-Path -Leaf $JarPath

# ---------- 前提检查 ----------
if (-not (Test-PortListening 3306)) {
    throw 'MySQL 未就绪：本机 3306 没有监听。请先启动 MySQL 后重试（脚本不会替你启动）。'
}
if (-not (Test-PortListening 6379)) {
    throw 'Redis 未就绪：本机 6379 没有监听。请先启动 Redis 后重试（脚本不会替你启动）。'
}

# ---------- 后端：空闲则启动，被占用则复用 ----------
$backendPid = 0
$backendReused = $false
$backendWrapperPid = 0

if (Test-PortListening $Port) {
    $ownerPid = Get-ListeningPid $Port
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$ownerPid" -ErrorAction SilentlyContinue
    $looksLikeZhiyu = $false
    if ($owner -and $owner.Name -eq 'java.exe' -and $owner.CommandLine -and $owner.CommandLine -match 'zhiyu') {
        $looksLikeZhiyu = $true
    }
    if (-not $looksLikeZhiyu) {
        $looksLikeZhiyu = Test-HttpOk "http://127.0.0.1:$Port/api/crisis/hotlines" 5
    }
    if (-not $looksLikeZhiyu) {
        throw "端口 $Port 被 PID $ownerPid 占用，但不像织愈后端（健康检查未通过）。已停止，未结束任何进程。"
    }
    $backendReused = $true
    $backendPid = $ownerPid
    Write-Host "[复用] 端口 $Port 已有后端 PID $ownerPid，不重复启动、不结束进程。" -ForegroundColor Yellow
    Write-Host '       若该进程启动时没带跨域白名单，前端仍会跨域失败（本脚本不会重启它）。'
} else {
    if (Test-Path $backendLog) { Remove-Item -LiteralPath $backendLog -Force -ErrorAction SilentlyContinue }
    $inner = "& '$JavaExe' '-Dfile.encoding=UTF-8' -jar '$JarPath' '--spring.profiles.active=dev' '--server.port=$Port' '--zhiyu.cors.allowed-origins=$CorsAllowedOrigins' *> '$backendLog'"
    $backendWrapperPid = Start-DetachedPowerShell $inner $backendRoot
    $backendPid = Get-ChildProcessId $backendWrapperPid 'java.exe'
    Write-Host "[启动] 后端 java PID $backendPid（wrapper $backendWrapperPid），日志 $backendLog"

    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 3
        if (Test-HttpOk "http://127.0.0.1:$Port/api/crisis/hotlines" 3) {
            $ready = $true
            break
        }
    }
    if (-not $ready) {
        $tail = ''
        if (Test-Path $backendLog) {
            $tail = (Get-Content $backendLog -Encoding UTF8 -Tail 15 -ErrorAction SilentlyContinue) -join "`n"
        }
        throw "后端 120 秒内未就绪。日志：$backendLog`n$tail"
    }
    if ($backendPid -le 0) { $backendPid = Get-ListeningPid $Port }
    Write-Host '[就绪] 本机后端 /api/crisis/hotlines 返回 200'
}

# ---------- 具名隧道：已存在同名进程则复用 ----------
$tunnelPid = 0
$tunnelReused = $false
$tunnelWrapperPid = 0

$runningTunnel = Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like ('*' + $TunnelName + '*') -and $_.CommandLine -like '*tunnel*' } |
    Select-Object -First 1

if ($runningTunnel) {
    $tunnelReused = $true
    $tunnelPid = [int]$runningTunnel.ProcessId
    Write-Host "[复用] 已有 $TunnelName 隧道进程 PID $tunnelPid，不重复启动。" -ForegroundColor Yellow
} else {
    if (Test-Path $tunnelLog) { Remove-Item -LiteralPath $tunnelLog -Force -ErrorAction SilentlyContinue }
    $inner = "& '$CloudflaredExe' tunnel --config '$TunnelConfig' run $TunnelName *> '$tunnelLog'"
    $tunnelWrapperPid = Start-DetachedPowerShell $inner $repoRoot
    $tunnelPid = Get-ChildProcessId $tunnelWrapperPid 'cloudflared.exe'
    Write-Host "[启动] 隧道 cloudflared PID $tunnelPid（wrapper $tunnelWrapperPid），日志 $tunnelLog"

    $registered = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        if (Test-Path $tunnelLog) {
            $text = Get-Content $tunnelLog -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
            if ($text -and $text -match 'Registered tunnel connection') {
                $registered = $true
                break
            }
        }
        if ($tunnelPid -gt 0 -and -not (Get-Process -Id $tunnelPid -ErrorAction SilentlyContinue)) { break }
    }
    if (-not $registered) {
        $tail = ''
        if (Test-Path $tunnelLog) {
            $tail = (Get-Content $tunnelLog -Encoding UTF8 -Tail 15 -ErrorAction SilentlyContinue) -join "`n"
        }
        throw "隧道 60 秒内未注册成功。日志：$tunnelLog`n$tail"
    }
    Write-Host '[就绪] 隧道已注册到 Cloudflare'
}

# ---------- 公网自检 ----------
$publicOk = $false
$publicBody = ''
if ($Port -ne 8080) {
    Write-Host "[跳过] 具名隧道固定指向 127.0.0.1:8080，端口 $Port 仅用于启停验证，不做公网自检。" -ForegroundColor Yellow
} else {
    $checkUrl = $PublicUrl.TrimEnd('/') + '/api/crisis/hotlines'
    for ($i = 0; $i -lt 12; $i++) {
        try {
            $resp = Invoke-WebRequest -Uri $checkUrl -UseBasicParsing -TimeoutSec 20
            $publicBody = $resp.Content
            if ($resp.StatusCode -eq 200 -and $resp.Content -match '"code"\s*:\s*0') {
                $publicOk = $true
                break
            }
        } catch {
        }
        Start-Sleep -Seconds 10
    }
}

# ---------- 记录自己拉起的 PID ----------
$state = [ordered]@{
    startedAt = (Get-Date).ToString('s')
    port = $Port
    publicUrl = $PublicUrl
    backend = [ordered]@{
        pid = $(if ($backendReused) { 0 } else { $backendPid })
        name = 'java.exe'
        marker = $jarMarker
        reused = $backendReused
        wrapperPid = $backendWrapperPid
        log = $backendLog
    }
    tunnel = [ordered]@{
        pid = $(if ($tunnelReused) { 0 } else { $tunnelPid })
        name = 'cloudflared.exe'
        marker = $TunnelName
        reused = $tunnelReused
        wrapperPid = $tunnelWrapperPid
        log = $tunnelLog
    }
}
$state | ConvertTo-Json -Depth 4 | Set-Content -Path $stateFile -Encoding UTF8

Write-Host ''
Write-Host '================ 演示环境已启动 ================' -ForegroundColor Green
Write-Host "公网地址   : $PublicUrl"
Write-Host "后端       : $(if ($backendReused) { "复用 PID $backendPid" } else { "PID $backendPid（本脚本启动）" })"
Write-Host "隧道       : $(if ($tunnelReused) { "复用 PID $tunnelPid" } else { "PID $tunnelPid（本脚本启动）" })"
Write-Host "公网自检   : $(if ($Port -ne 8080) { '已跳过（验证端口）' } elseif ($publicOk) { 'HTTP 200 且 code:0' } else { '失败' })" -ForegroundColor $(if ($Port -ne 8080) { 'Yellow' } elseif ($publicOk) { 'Green' } else { 'Red' })
Write-Host "状态文件   : $stateFile"
Write-Host "后端日志   : $backendLog"
Write-Host "隧道日志   : $tunnelLog"
Write-Host '================================================' -ForegroundColor Green

if ($Port -eq 8080 -and -not $publicOk) {
    Write-Host ''
    Write-Host '自检失败：请先看上面的隧道日志与后端日志，不要急着反复重启。' -ForegroundColor Red
    if ($publicBody) { Write-Host ('响应片段：' + $publicBody.Substring(0, [Math]::Min(200, $publicBody.Length))) }
    exit 1
}
exit 0
