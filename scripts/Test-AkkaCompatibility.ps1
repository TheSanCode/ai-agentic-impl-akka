param(
    [ValidateSet('akka', 'combined')]
    [string]$Probe = 'akka',
    [string]$JavaHome,
    [switch]$ValidateOnly,
    # Resolve into an empty temporary local repository to prove vendor-repository download.
    [switch]$IsolatedCache
)

$ErrorActionPreference = 'Stop'

# Windows PowerShell 5.1 turns native stderr (e.g. JDK 25 sun.misc.Unsafe
# warnings from Maven) into terminating errors under 'Stop'; capture it as text.
function Invoke-NativeCapture {
    param([string]$FilePath, [string[]]$Arguments)
    $ErrorActionPreference = 'Continue'
    & $FilePath @Arguments 2>&1 | ForEach-Object { $_.ToString() }
}

if (-not $JavaHome) {
    $JavaHome = $env:JAVA_HOME
}

$javaHomes = @()
if ($JavaHome) {
    $javaHomes += $JavaHome
}
$javaHomes += Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium\jdk-25.0.4.1+1'

$selectedJavaHome = $null
foreach ($candidate in $javaHomes | Select-Object -Unique) {
    $javac = Join-Path $candidate 'bin\javac.exe'
    if (Test-Path $javac) {
        $version = (Invoke-NativeCapture $javac @('-version') | Out-String)
        if ($version -match '\b25\.') {
            $selectedJavaHome = $candidate
            break
        }
    }
}

if (-not $selectedJavaHome) {
    throw 'JDK 25 was not found. Pass -JavaHome <JDK-25-path> or install Temurin 25 at the documented default path.'
}

$env:JAVA_HOME = $selectedJavaHome
$env:Path = "$(Join-Path $selectedJavaHome 'bin');$env:Path"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$mavenWrapper = Join-Path $repoRoot 'mvnw.cmd'
$pom = Join-Path $repoRoot "compatibility\$Probe\pom.xml"
$promptedForRepositoryUrl = $false

$mavenVersion = (Invoke-NativeCapture $mavenWrapper @('--version') | Out-String)
Write-Output $mavenVersion.TrimEnd()
if ($mavenVersion -notmatch 'Java version:\s+25(?:\.|,)') {
    throw 'Maven did not start on Java 25; refusing to compile the Java 25 probe.'
}

try {
    $tempSettings = Join-Path ([IO.Path]::GetTempPath()) (
        'agentica-maven-settings-{0}.xml' -f [guid]::NewGuid().ToString('N')
    )
    [IO.File]::WriteAllText(
        $tempSettings,
        '<?xml version="1.0" encoding="UTF-8"?><settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"></settings>',
        [Text.UTF8Encoding]::new($false)
    )

    $profileArguments = @(
        '-s', $tempSettings,
        '-P', 'akka-repository',
        '-B', '-ntp',
        '-f', $pom,
        'help:active-profiles'
    )
    $profileOutput = Invoke-NativeCapture $mavenWrapper $profileArguments
    $profileExitCode = $LASTEXITCODE
    foreach ($line in $profileOutput) {
        $safeLine = $line.ToString() -replace '(?i)https?://\S+', '[repository URL redacted]'
        Write-Output $safeLine
    }
    if ($profileExitCode -ne 0) {
        throw "Could not verify the Akka Maven profile (exit code $profileExitCode)."
    }
    if (($profileOutput -join "`n") -notmatch 'akka-repository \(source:') {
        throw 'The Akka Maven profile was not active; refusing to run the probe.'
    }

    if ($ValidateOnly) {
        return
    }

    if (-not $env:AKKA_REPOSITORY_URL) {
        $secureUrl = Read-Host 'Enter the authorized Akka HTTPS repository URL' -AsSecureString
        $urlPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureUrl)
        try {
            $env:AKKA_REPOSITORY_URL = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($urlPointer)
            $promptedForRepositoryUrl = $true
        }
        finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($urlPointer)
            Remove-Variable secureUrl -ErrorAction SilentlyContinue
        }
    }

    $repositoryUri = $null
    if (-not [Uri]::TryCreate($env:AKKA_REPOSITORY_URL, [UriKind]::Absolute, [ref]$repositoryUri) -or
        $repositoryUri.Scheme -ne 'https') {
        throw 'AKKA_REPOSITORY_URL must be an absolute HTTPS URL.'
    }

    $arguments = @(
        '-s', $tempSettings,
        '-P', 'akka-repository',
        '-U', '-B', '-ntp',
        '-f', $pom,
        'verify'
    )
    if ($IsolatedCache) {
        $isolatedRepository = Join-Path ([IO.Path]::GetTempPath()) (
            'agentica-m2-{0}' -f [guid]::NewGuid().ToString('N')
        )
        New-Item -ItemType Directory -Path $isolatedRepository | Out-Null
        $arguments = @("-Dmaven.repo.local=$isolatedRepository") + $arguments
        Write-Output 'Using an empty temporary Maven repository; all dependencies will be downloaded.'
    }
    $output = Invoke-NativeCapture $mavenWrapper $arguments
    $exitCode = $LASTEXITCODE
    foreach ($line in $output) {
        $safeLine = $line.ToString() -replace '(?i)https?://\S+', '[repository URL redacted]'
        $safeLine = $safeLine -replace '(?i)(password|token|credential)\s*[=:]\s*\S+', '$1=[REDACTED]'
        Write-Output $safeLine
    }

    if ($exitCode -ne 0) {
        throw "Akka compatibility probe failed with exit code $exitCode. See redacted Maven output above."
    }

    if ($IsolatedCache) {
        $akkaJar = Join-Path $isolatedRepository 'com\typesafe\akka\akka-actor-typed_2.13\2.10.23\akka-actor-typed_2.13-2.10.23.jar'
        $origin = Join-Path (Split-Path $akkaJar) '_remote.repositories'
        if (-not (Test-Path -LiteralPath $akkaJar) -or -not (Test-Path -LiteralPath $origin) -or
            -not (Select-String -LiteralPath $origin -Pattern '\.jar>akka-repository=' -Quiet)) {
            throw 'Akka was not downloaded from repository ID akka-repository into the isolated cache.'
        }
        Write-Output 'VERIFIED: akka-actor-typed_2.13-2.10.23.jar freshly downloaded from repository ID akka-repository.'
    }
}
finally {
    if ($promptedForRepositoryUrl) {
        Remove-Item Env:AKKA_REPOSITORY_URL -ErrorAction SilentlyContinue
    }
    if ($tempSettings -and (Test-Path -LiteralPath $tempSettings)) {
        Remove-Item -LiteralPath $tempSettings
    }
    if ($isolatedRepository -and (Test-Path -LiteralPath $isolatedRepository)) {
        Remove-Item -LiteralPath $isolatedRepository -Recurse -Force
    }
}
