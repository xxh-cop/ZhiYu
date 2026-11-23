<#
.SYNOPSIS
    织愈本机演示启动链：注入跨域白名单 -> 启动后端 -> 建立 HTTPS 隧道 -> 打印公网地址。

.DESCRIPTION
    仅新增的演示辅助脚本，不修改 .env、application*.yml 或任何现有启动方式。
    后端通过命令行参数注入 --zhiyu.cors.allowed-origins，不写入仓库。
    运行日志与状态统一放在仓库根目录 .ai-tmp/（已被 .gitignore 忽略）。

    零影响约定：
      * 端口被占用时不抢端口、不结束任何进程，只提示后跳过启动后端；
      * 同一端口 + 同一隧道目标已有隧道时不重复启动；
      * 不读取、不覆盖 .env。

.PARAMETER CorsAllowedOrigins
    逗号分隔的跨域白名单，支持通配模式。
    默认：两个已部署站点 + http://localhost:* + http://127.0.0.1:*

.PARAMETER Port
    本机后端端口，默认 8080。

.PARAMETER RemotePort
    隧道远端端口，默认 80。

.PARAMETER SshTarget
    隧道服务 SSH 目标，默认 serveo.net（localhost.run 可作备选）。

.PARAMETER JavaExe
    java.exe 绝对路径，默认从 JAVA_HOME / PATH 解析。

.PARAMETER JarPath
    后端 jar 路径，默认取 target/zhiyu-backend-0.1.0-SNAPSHOT.jar。

.PARAMETER StateDir
    运行状态目录，默认仓库根目录下的 .ai-tmp。

.NOTES
    依赖工作区已存在但尚未提交的跨域开关 zhiyu.cors.allowed-origins。
    隧道域名每次重连都会变，本脚本不会修改前端、也不会触发前端重新构建。
#>
[CmdletBinding()]
param(
    [int]$Port = 0,
    [string]$RemotePort = '',
    [string]$SshTarget = '',
    [string]$CorsAllowedOrigins = '',
    [string]$JavaExe = '',
    [string]$JarPath = '',
    [string]$StateDir = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $backendRoot

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

function Find-TunnelUrl([string]$text) {
    # 常见免费隧道域名后缀优先，避免误抓帮助文档链接
    $urlMatches = [regex]::Matches($text, 'https://[A-Za-z0-9][A-Za-z0-9.-]+')
    foreach ($m in $urlMatches) {
        $u = $m.Value
        if ($u -match '\.app\.lhr\.life$' -or
            $u -match '\.serveousercontent\.com$' -or
            $u -match '\.pinggy\.link$') {
            return $u
        }
    }
    # 其他隧道服务的通用兜底
    $fallback = [regex]::Match(
        $text,
        '(?:Forwarding HTTP traffic from|tunneled with tls termination,)\s*(https://[^\s]+)')
    if ($fallback.Success) { return $fallback.Groups[1].Value }
    return $null
}

# ---------- 参数默认值（环境变量可覆盖，均不落盘） ----------
if ($Port -le 0) {
    $envPort = Get-EnvOrEmpty 'ZHIYU_DEMO_PORT'
    $parsed = 0
    if ($envPort -and [int]::TryParse($envPort, [ref]$parsed) -and $parsed -gt 0) {
        $Port = $parsed
    } else {
        $Port = 8080
    }
}

if (-not $RemotePort) {
    $RemotePort = Get-EnvOrEmpty 'ZHIYU_TUNNEL_REMOTE_PORT'
    if (-not $RemotePort) { $RemotePort = '80' }
}

if (-not $SshTarget) {
    $SshTarget = Get-EnvOrEmpty 'ZHIYU_TUNNEL_SSH_TARGET'
    if (-not $SshTarget) { $SshTarget = 'serveo.net' }
}

if (-not $CorsAllowedOrigins) {
    $CorsAllowedOrigins = Get-EnvOrEmpty 'ZHIYU_CORS_ALLOWED_ORIGINS'
    if (-not $CorsAllowedOrigins) {
        $CorsAllowedOrigins = 'http://localhost:*,http://127.0.0.1:*,https://37lepx8drvjlbp6is2i.traesite.cn,https://37leqmgwx2l4gsn6y55.traesite.cn'
    }
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
    throw '未找到 java.exe。请安装 JDK 17，或设置 JAVA_HOME，或用 -JavaExe 指定绝对路径。'
}

if (-not $JarPath) { $JarPath = Get-EnvOrEmpty 'ZHIYU_DEMO_JAR' }
if (-not $JarPath) {
    $JarPath = Join-Path $backendRoot 'target\zhiyu-backend-0.1.0-SNAPSHOT.jar'
}
if (-not (Test-Path $JarPath)) {
    $latest = Get-ChildItem (Join-Path $backendRoot 'target') -Filter '*.jar' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($latest) { $JarPath = $latest.FullName }
}
if (-not (Test-Path $JarPath)) {
    throw '未找到后端 jar。请先在 zhiyu-backend 目录执行：mvn package -DskipTests'
}

if (-not $StateDir) { $StateDir = Join-Path $repoRoot '.ai-tmp' }
if (-not (Test-Path $StateDir)) {
    New-Item -ItemType Directory -Path $StateDir -Force | Out-Null
}

$sshExe = (Get-Command ssh.exe -ErrorAction SilentlyContinue).Source
if (-not $sshExe) { $sshExe = 'C:\Windows\System32\OpenSSH\ssh.exe' }
if (-not (Test-Path $sshExe)) {
    throw '未找到 ssh.exe。请确认 Windows OpenSSH 客户端已安装。'
}

$stateFile = Join-Path $StateDir "demo-tunnel-$Port.json"
$knownHosts = Join-Path $StateDir 'demo-tunnel-known_hosts'
$backendLog = Join-Path $StateDir "demo-backend-$Port.log"
$tunnelLog = Join-Path $StateDir "demo-tunnel-$Port.log"

# ---------- 前提检查（只检测，不启动任何服务） ----------
if (-not (Test-PortListening 3306)) {
    throw 'MySQL 未就绪：本机 3306 端口没有监听。请先启动 MySQL 后重试（脚本不会替你启动）。'
}
if (-not (Test-PortListening 6379)) {
    throw 'Redis 未就绪：本机 6379 端口没有监听。请先启动 Redis 后重试（脚本不会替你启动）。'
}

# ---------- 已有隧道则不重复启动 ----------
$existing = $null
if (Test-Path $stateFile) {
    try {
        $saved = Get-Content $stateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        $saved = $null
    }
    if ($saved -and $saved.tunnelPid) {
        $savedProc = Get-Process -Id ([int]$saved.tunnelPid) -ErrorAction SilentlyContinue
        if ($savedProc -and $savedProc.ProcessName -eq 'ssh') {
            $existing = $saved
        }
    }
}
if (-not $existing) {
    # 不区分隧道服务商：只要已有 ssh 把本机同一后端端口反代出去，就不再起第二条
    $pattern = '-R\s*\S*:127\.0\.0\.1:' + $Port + '\b'
    $sshProcesses = Get-CimInstance Win32_Process -Filter "Name='ssh.exe'" -ErrorAction SilentlyContinue
    foreach ($proc in $sshProcesses) {
        if ($proc.CommandLine -and $proc.CommandLine -match $pattern) {
            $existing = [pscustomobject]@{
                tunnelPid = $proc.ProcessId
                url       = $null
                note      = '检测到已有隧道进程（非本脚本状态文件记录）'
            }
            break
        }
    }
}
if ($existing) {
    Write-Host '[跳过] 已经有一条到本机同端口的隧道在运行，本次不重复启动第二条。' -ForegroundColor Yellow
    Write-Host "        隧道 PID：$($existing.tunnelPid)"
    if ($existing.url) {
        Write-Host "        公网地址：$($existing.url)" -ForegroundColor Green
    } else {
        Write-Host '        公网地址未知（该进程不是本脚本启动）；请查看它自己的启动日志，或先结束它再运行本脚本。'
    }
    exit 0
}

# ---------- 启动后端（端口空闲时） ----------
$backendPid = 0
$backendWrapperPid = 0
$backendReused = $false
if (Test-PortListening $Port) {
    $owner = Get-ListeningPid $Port
    $backendReused = $true
    Write-Host "[跳过] 端口 $Port 已被 PID $owner 占用：不启动后端，也不会结束该进程。" -ForegroundColor Yellow
    Write-Host '        注意：如果它不是带跨域白名单启动的织愈后端，两个站点仍会跨域失败。'
    if (-not (Test-HttpOk "http://127.0.0.1:$Port/api/crisis/hotlines" 5)) {
        throw "端口 $Port 上的服务未通过 /api/crisis/hotlines 健康检查，已停止（未结束任何进程）。"
    }
} else {
    if (Test-Path $backendLog) { Remove-Item -LiteralPath $backendLog -Force -ErrorAction SilentlyContinue }
    $inner = "& '$JavaExe' '-Dfile.encoding=UTF-8' -jar '$JarPath' '--spring.profiles.active=dev' '--server.port=$Port' '--zhiyu.cors.allowed-origins=$CorsAllowedOrigins' *> '$backendLog'"
    $wrapperPid = Start-DetachedPowerShell $inner $backendRoot
    $backendWrapperPid = $wrapperPid
    $backendPid = Get-ChildProcessId $wrapperPid 'java.exe'
    Write-Host "[启动] 后端：wrapper PID $wrapperPid，java PID $backendPid，日志 $backendLog"

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
        throw "后端在 120 秒内未就绪。日志尾部：`n$tail"
    }
    Write-Host '[就绪] 本机后端 /api/crisis/hotlines 返回 200'
    if ($backendPid -le 0) { $backendPid = Get-ListeningPid $Port }
}

# ---------- 建立隧道 ----------
if (Test-Path $tunnelLog) { Remove-Item -LiteralPath $tunnelLog -Force -ErrorAction SilentlyContinue }
$tunnelInner = "& '$sshExe' -T -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile='$knownHosts' -o ServerAliveInterval=30 -o ExitOnForwardFailure=yes -R ${RemotePort}:127.0.0.1:${Port} $SshTarget *> '$tunnelLog'"
$tunnelWrapperPid = Start-DetachedPowerShell $tunnelInner $repoRoot
$tunnelPid = Get-ChildProcessId $tunnelWrapperPid 'ssh.exe'
Write-Host "[启动] 隧道：wrapper PID $tunnelWrapperPid，ssh PID $tunnelPid，日志 $tunnelLog"

$url = $null
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 1
    if (Test-Path $tunnelLog) {
        $text = Get-Content $tunnelLog -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
        if ($text) {
            $url = Find-TunnelUrl $text
            if ($url) { break }
        }
    }
    if ($tunnelPid -gt 0 -and -not (Get-Process -Id $tunnelPid -ErrorAction SilentlyContinue)) { break }
}
if (-not $url) {
    $tail = ''
    if (Test-Path $tunnelLog) {
        $tail = (Get-Content $tunnelLog -Encoding UTF8 -Tail 15 -ErrorAction SilentlyContinue) -join "`n"
    }
    throw "隧道未返回公网地址（ssh PID $tunnelPid）。日志：`n$tail"
}
if ($tunnelPid -le 0) { $tunnelPid = Get-ChildProcessId $tunnelWrapperPid 'ssh.exe' }

# ---------- 公网验证 ----------
$publicOk = $false
for ($i = 0; $i -lt 5; $i++) {
    if (Test-HttpOk "$url/api/crisis/hotlines" 20) {
        $publicOk = $true
        break
    }
    Start-Sleep -Seconds 3
}

# ---------- 记录状态并输出 ----------
$state = [ordered]@{
    port              = $Port
    remotePort        = $RemotePort
    sshTarget         = $SshTarget
    url               = $url
    backendPid        = $backendPid
    backendReused     = $backendReused
    backendWrapperPid = $backendWrapperPid
    tunnelPid         = $tunnelPid
    tunnelWrapperPid  = $tunnelWrapperPid
    backendLog        = $backendLog
    tunnelLog         = $tunnelLog
    startedAt         = (Get-Date).ToString('s')
}
$state | ConvertTo-Json | Set-Content -Path $stateFile -Encoding UTF8

Write-Host ''
Write-Host '================ 演示通道已建立 ================' -ForegroundColor Green
Write-Host "公网 Base URL : $url" -ForegroundColor Green
Write-Host "后端端口      : $Port（$(if ($backendReused) { '复用已有进程' } else { "java PID $backendPid" })）"
Write-Host "隧道进程      : ssh PID $tunnelPid"
Write-Host "跨域白名单    : $CorsAllowedOrigins"
Write-Host "公网自检      : $(if ($publicOk) { '/api/crisis/hotlines 返回 200' } else { '未通过（请查看隧道日志，稍后重试）' })" -ForegroundColor $(if ($publicOk) { 'Green' } else { 'Yellow' })
Write-Host "状态文件      : $stateFile"
Write-Host '================================================' -ForegroundColor Green
Write-Host ''
Write-Host '重要提醒：隧道域名每次重连都会变，本脚本不会修改前端或触发重新构建。'
Write-Host '         两个站点必须把 VITE_API_BASE_URL 改成上面的域名后重新构建部署。'
Write-Host ''
Write-Host '关闭方式（按 PID，不要按端口盲杀）：'
Write-Host "  Stop-Process -Id $tunnelPid -Force"
if (-not $backendReused -and $backendPid -gt 0) {
    Write-Host "  Stop-Process -Id $backendPid -Force"
}

if (-not $publicOk) { exit 1 }
exit 0
