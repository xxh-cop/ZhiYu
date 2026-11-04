# dialogue-gate.ps1 — AK1 gray-release gate (2026-11-04)
#
# Purpose: repeatable gate before enabling zhiyu.dialogue.listening-via-commander.
# Runs the eval baseline suites and fails (exit 1) unless green:
#   - mock mode: CompanionEvalMockTest (L1 red-line 15/15 asserted in-test, zero FAIL allowed)
#   - real mode: CompanionEvalRealIT  (real provider; L1 any FAIL = veto)
#
# Gate policy (decision recorded in docs): if the gate fails, the switches MUST stay off.
# We deliberately use "deploy-check failure" (this script's exit code) rather than a
# boot-time self-check, because running the eval needs network + minutes of latency and
# must never delay or block application startup.
#
# Usage:
#   powershell -File scripts/dialogue-gate.ps1            # mock + real (real skipped if no key)
#   powershell -File scripts/dialogue-gate.ps1 -Mode mock
#   powershell -File scripts/dialogue-gate.ps1 -Mode real
#
# Output: writes .ai-tmp/dialogue-gate-result.json; eval reports land in docs/eval-runs/
# (produced by EvalRunnerEngine for real mode). Never prints secrets.

param(
    [ValidateSet("mock", "real", "both")]
    [string]$Mode = "both"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "zhiyu-backend"

# Load .env into process env (no value is ever printed)
$envFile = Join-Path $root ".env"
if (Test-Path $envFile) {
    foreach ($line in [System.IO.File]::ReadAllLines($envFile, [System.Text.Encoding]::UTF8)) {
        $t = $line.Trim()
        if ($t.Length -eq 0 -or $t.StartsWith("#")) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $name = $t.Substring(0, $eq).Trim()
        $value = $t.Substring($eq + 1).Trim()
        if ($name.StartsWith("export ")) { $name = $name.Substring(7).Trim() }
        if ($name -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") { continue }
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

$hasKey = -not [string]::IsNullOrEmpty($env:DEEPSEEK_API_KEY) -and $env:DEEPSEEK_API_KEY -ne "sk-placeholder"
$result = [ordered]@{ timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss"); mock = "skipped"; real = "skipped"; gate = "FAIL" }

Push-Location $backend
try {
    if ($Mode -eq "mock" -or $Mode -eq "both") {
        Write-Host "[gate] mock eval (CompanionEvalMockTest)..."
        & mvn -q test "-Dmaven.repo.local=../.ai-tmp/m2" "-Dtest=CompanionEvalMockTest" | Out-Null
        if ($LASTEXITCODE -eq 0) { $result.mock = "PASS" } else { $result.mock = "FAIL" }
    }
    if (($Mode -eq "real" -or $Mode -eq "both")) {
        if (-not $hasKey) {
            Write-Host "[gate] real eval SKIPPED: DEEPSEEK_API_KEY missing/placeholder"
            $result.real = "SKIPPED(no key)"
        } else {
            Write-Host "[gate] real eval (CompanionEvalRealIT)..."
            & mvn -q test "-Dmaven.repo.local=../.ai-tmp/m2" "-Dtest=CompanionEvalRealIT" "-Dzhiyu.eval.tag=gate-ak" | Out-Null
            if ($LASTEXITCODE -eq 0) { $result.real = "PASS" } else { $result.real = "FAIL" }
        }
    }
} finally {
    Pop-Location
}

$mockOk = $result.mock -eq "PASS" -or $result.mock -eq "skipped"
$realOk = $result.real -eq "PASS" -or $result.real -eq "skipped" -or $result.real.StartsWith("SKIPPED")
if ($mockOk -and $realOk -and ($result.mock -eq "PASS" -or $result.real -eq "PASS")) {
    $result.gate = "PASS"
}

$outDir = Join-Path $root ".ai-tmp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
[System.IO.File]::WriteAllText((Join-Path $outDir "dialogue-gate-result.json"),
    ($result | ConvertTo-Json), [System.Text.Encoding]::UTF8)

Write-Host ("[gate] mock={0} real={1} => GATE {2}" -f $result.mock, $result.real, $result.gate)
if ($result.gate -ne "PASS") { exit 1 }
