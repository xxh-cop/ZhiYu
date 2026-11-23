<#
.SYNOPSIS
    织愈本机演示一键停止：只停 start-demo.ps1 记录在状态文件里的进程。

.DESCRIPTION
    安全约定：
      * 只处理 .ai-tmp/demo-stack-state.json 里记录且 pid > 0 的进程；
      * 停止前校验进程名与命令行标记，防止 PID 复用误杀；
      * 用户自己手动启动的进程（例如现有 8080 后端、手动起的 cloudflared）不在状态文件里，不会被处理；
      * 停止后复查本机端口与公网地址状态，如实输出。

    不修改 .env、application*.yml，也不改动任何现有启动方式。

.PARAMETER StateFile
    状态文件，默认 <仓库根>\.ai-tmp\demo-stack-state.json。

.PARAMETER PublicUrl
    公网地址，默认读取状态文件；取不到时用 https://api.xiangjuntao.ltd。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\zhiyu-backend\scripts\stop-demo.ps1
#>
[CmdletBinding()]
param(
    [string]$StateFile = '',
    [string]$PublicUrl = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $backendRoot
if (-not $StateFile) { $StateFile = Join-Path (Join-Path $repoRoot '.ai-tmp') 'demo-stack-state.json' }

function Test-HttpOk([string]$url, [int]$timeoutSeconds) {
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec $timeoutSeconds
        return ($resp.StatusCode -eq 200)
    } catch {
        return $false
    }
}

function Test-HttpCodeZero([string]$url, [int]$timeoutSeconds) {
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec $timeoutSeconds
        return ($resp.StatusCode -eq 200 -and $resp.Content -match '"code"\s*:\s*0')
    } catch {
        return $false
    }
}

function Get-OwnedProcess([int]$processId, [string]$expectedName, [string]$marker) {
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
    if (-not $proc) { return [pscustomobject]@{ status = 'missing'; process = $null } }
    if ($proc.Name -ne $expectedName) {
        return [pscustomobject]@{ status = 'mismatch'; process = $proc }
    }
    if (-not $proc.CommandLine -or $proc.CommandLine -notlike ('*' + $marker + '*')) {
        return [pscustomobject]@{ status = 'mismatch'; process = $proc }
    }
    return [pscustomobject]@{ status = 'ok'; process = $proc }
}

$port = 8080
$state = $null
if (Test-Path $StateFile) {
    try {
        $state = Get-Content $StateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        throw "状态文件无法解析：$StateFile（请人工检查，脚本不会猜测要停哪些 PID）"
    }
    if ($state.PSObject.Properties['port'] -and $state.port) { $port = [int]$state.port }
    if (-not $PublicUrl -and $state.PSObject.Properties['publicUrl'] -and $state.publicUrl) {
        $PublicUrl = [string]$state.publicUrl
    }
}
if (-not $PublicUrl) { $PublicUrl = 'https://api.xiangjuntao.ltd' }

if (-not $state) {
    Write-Host "[提示] 未找到状态文件：$StateFile" -ForegroundColor Yellow
    Write-Host '       说明没有由 start-demo.ps1 启动的进程需要停止；用户手动启动的进程不会被处理。'
} else {
    $refused = @()
    $handled = 0

    foreach ($key in @('backend', 'tunnel')) {
        if (-not $state.PSObject.Properties[$key]) { continue }
        $entry = $state.$key
        if (-not $entry -or -not $entry.PSObject.Properties['pid']) { continue }
        $pidValue = [int]$entry.pid

        if ($pidValue -le 0) {
            $reason = '复用模式'
            if ($entry.PSObject.Properties['reused'] -and $entry.reused) { $reason = '非本脚本启动，未处理' }
            Write-Host "[跳过] $key：$reason（PID 未记录）" -ForegroundColor Yellow
            continue
        }

        $check = Get-OwnedProcess $pidValue ([string]$entry.name) ([string]$entry.marker)
        switch ($check.status) {
            'missing' {
                Write-Host "[已停止] $key PID $pidValue 已不在运行。"
                $entry.pid = 0
                $handled++
            }
            'mismatch' {
                $actual = ''
                if ($check.process) { $actual = $check.process.Name }
                Write-Host "[拒绝] $key PID $pidValue 已不属于本项目（当前进程名：$actual），可能是 PID 复用，未结束。" -ForegroundColor Red
                $refused += $key
            }
            default {
                Write-Host "[停止] $key PID $pidValue（$($check.process.Name)）..." -NoNewline
                Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
                $stopped = $false
                for ($i = 0; $i -lt 20; $i++) {
                    Start-Sleep -Milliseconds 500
                    if (-not (Get-Process -Id $pidValue -ErrorAction SilentlyContinue)) { $stopped = $true; break }
                }
                if ($stopped) {
                    Write-Host ' 已完成' -ForegroundColor Green
                    $entry.pid = 0
                    $handled++
                } else {
                    Write-Host ' 失败（进程仍在）' -ForegroundColor Red
                    $refused += $key
                }
            }
        }
    }

    $state | Add-Member -NotePropertyName stoppedAt -NotePropertyValue (Get-Date).ToString('s') -Force
    $state | ConvertTo-Json -Depth 4 | Set-Content -Path $StateFile -Encoding UTF8
    if ($refused.Count -eq 0) {
        Remove-Item -LiteralPath $StateFile -Force -ErrorAction SilentlyContinue
        Write-Host "[清理] 状态文件已删除（本次处理 $handled 项）。"
    } else {
        Write-Host "[保留] 状态文件保留，未处理项：$($refused -join ', ')" -ForegroundColor Yellow
    }
}

# ---------- 停止后复查 ----------
Write-Host ''
Write-Host '================ 停止后状态 ================' -ForegroundColor Cyan
$listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($listener) {
    $ownerPid = [int]$listener.OwningProcess
    $ownerName = (Get-Process -Id $ownerPid -ErrorAction SilentlyContinue).ProcessName
    $healthy = Test-HttpOk "http://127.0.0.1:$port/api/crisis/hotlines" 5
    Write-Host "本机 $port : 仍在监听（PID $ownerPid / $ownerName，健康检查 $(if ($healthy) { '通过' } else { '未通过' })）"
} else {
    Write-Host "本机 $port : 未监听"
}

$checkUrl = $PublicUrl.TrimEnd('/') + '/api/crisis/hotlines'
if (Test-HttpCodeZero $checkUrl 20) {
    Write-Host "公网地址  : $PublicUrl 可访问（HTTP 200 且 code:0）"
} else {
    Write-Host "公网地址  : $PublicUrl 当前不可用（隧道可能已停止或尚未恢复）" -ForegroundColor Yellow
}

$cf = Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" -ErrorAction SilentlyContinue
if ($cf) {
    $names = ($cf | ForEach-Object { $_.ProcessId }) -join ', '
    Write-Host "cloudflared 进程：$names（不在状态文件里的不会被本脚本处理）"
} else {
    Write-Host 'cloudflared 进程：无'
}
Write-Host '============================================' -ForegroundColor Cyan

if ($state -and $refused.Count -gt 0) { exit 2 }
exit 0
