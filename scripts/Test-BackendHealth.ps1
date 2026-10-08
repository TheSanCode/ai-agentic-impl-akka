# Build with .\mvnw.cmd -B -ntp clean verify before running this smoke check.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaExecutable = Join-Path $env:JAVA_HOME 'bin/java.exe'
$applicationJar = Join-Path $projectRoot 'target/agenticawithakka-0.0.1-SNAPSHOT.jar'
$stdoutLog = Join-Path $projectRoot 'target/health-smoke.stdout.log'
$stderrLog = Join-Path $projectRoot 'target/health-smoke.stderr.log'
if (-not (Test-Path -LiteralPath $applicationJar)) {
    throw 'Packaged application is missing. Run the wrapper build first.'
}

$applicationProcess = Start-Process -FilePath $javaExecutable `
    -ArgumentList @('-jar', ('"' + $applicationJar + '"')) `
    -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
try {
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    $started = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($applicationProcess.HasExited) {
            throw "Application exited with code $($applicationProcess.ExitCode); inspect $stdoutLog and $stderrLog."
        }
        if (Select-String -LiteralPath $stdoutLog -Pattern 'Started AgenticaApplication' -Quiet) {
            $started = $true
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $started) {
        throw "Application did not start within 45 seconds; inspect $stdoutLog and $stderrLog."
    }
    $response = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/health' -TimeoutSec 5
    $health = $response.Content | ConvertFrom-Json
    if ($response.StatusCode -ne 200 -or $health.status -ne 'UP' -or
        $health.service -ne 'agenticawithakka' -or @($health.PSObject.Properties).Count -ne 2) {
        throw "Unexpected health response: $($response.Content)"
    }
    Write-Output "HTTP $($response.StatusCode) $($response.Content)"
}
finally {
    if (-not $applicationProcess.HasExited) {
        Stop-Process -Id $applicationProcess.Id
        $applicationProcess.WaitForExit()
    }
    Write-Output 'Smoke-check application process stopped.'
}
