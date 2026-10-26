# Run from any directory. Requires Java 17 and Maven on PATH (or an existing JAVA_HOME).
# Optional .env uses literal KEY=value lines; no shell expressions are evaluated.
$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($rawLine in [IO.File]::ReadAllLines($envFile, [Text.Encoding]::UTF8)) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith('#')) { continue }
        $parts = $line -split '=', 2
        if ($parts.Length -ne 2 -or $parts[0].Trim() -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            throw 'Invalid .env line. Use KEY=value without shell expressions.'
        }
        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        # Explicit process settings take precedence over the local convenience file.
        if ($null -eq [Environment]::GetEnvironmentVariable($key, 'Process')) {
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
    Write-Host 'Loaded optional local configuration; values are not printed.'
}
if ($env:JAVA_HOME -and -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'JAVA_HOME is invalid. Configure a Java 17 installation.'
}
Get-Command mvn -ErrorAction Stop | Out-Null
Push-Location (Join-Path $PSScriptRoot 'zhiyu-backend')
try {
    & mvn spring-boot:run "-Dmaven.repo.local=../.ai-tmp/m2"
    $backendExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $backendExitCode
