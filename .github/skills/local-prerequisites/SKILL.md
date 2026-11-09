---
name: local-prerequisites
description: Detect the host OS and check local development prerequisites for this repository. On Windows, find WinGet and guide confirmation-gated installation of missing JDK 25, WSL2, Podman and Ollama. Use for fresh-clone setup, missing-tool checks or Windows installation requests.
---

# Local prerequisite setup

## Scope and safeguards

Read `README.md`, `docs/local-prerequisites.md` and `docs/implementation/backend-scaffold.md` from the repository root. Treat historical installation tables as prior observations, not evidence about the current machine.

Default to read-only detection. Ask whether the user needs the backend only or the full local lab if the request does not specify it. Present the missing tools, package IDs and exact proposed commands, then obtain explicit confirmation before installation. A request to check tools or load this skill is not installation approval.

Do not upgrade existing tools, change persistent PATH/JAVA_HOME, elevate, restart Windows, initialize a Podman machine, download models or start services without approval for those actions. Never uninstall tools or substitute unapproved installers. Respect repository branch and conversation-recording instructions.

## 1. Detect the operating system

Use the execution environment's OS information; do not infer the OS from the shell name. If unavailable, in PowerShell use:

```powershell
[System.Runtime.InteropServices.RuntimeInformation]::OSDescription
[System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
    [System.Runtime.InteropServices.OSPlatform]::Windows)
```

In a POSIX shell, use `uname -s` to distinguish Linux and macOS. WSL is a Linux execution environment: do not attempt to install host Windows tools from it. For Linux, macOS or an unknown OS, report the detected OS and stop before installation. This skill currently implements only Windows setup; do not silently select apt, brew or another package manager.

## 2. Find WinGet on Windows

Run in PowerShell:

```powershell
$winget = Get-Command winget.exe -CommandType Application -ErrorAction SilentlyContinue
if ($winget) {
    $wingetPath = $winget.Source
} else {
    $wingetPath = Join-Path $env:LOCALAPPDATA 'Microsoft\WindowsApps\winget.exe'
    if (-not (Test-Path -LiteralPath $wingetPath -PathType Leaf)) {
        throw 'WinGet was not found. Install or update Microsoft App Installer, then reopen PowerShell.'
    }
}
& $wingetPath --version
if ($LASTEXITCODE -ne 0) {
    throw 'WinGet was located but could not run. Resolve App Installer or execution-alias configuration before continuing.'
}
```

Report the resolved executable and version. If absent or unusable, direct the user to [Microsoft's WinGet installation guidance](https://learn.microsoft.com/en-us/windows/package-manager/winget/#install-winget). Do not fetch or execute an arbitrary bootstrap script. Missing WinGet blocks package installation, not the remaining read-only tool checks.

## 3. Check installed tools and readiness

Use `Get-Command` and version commands for Git, Java/Javac, Node.js/npm, Podman or Docker, and Ollama. Use `npm.cmd` if PowerShell blocks the npm script wrapper; do not weaken execution policy.

- Backend: require Java/Javac 25 and a consistent `JAVA_HOME`. Check `.\mvnw.cmd --version`; Maven must select Java 25. The repository wrapper replaces a host Maven installation.
- If a tool is not on PATH, check `JAVA_HOME`, relevant installed-application records and conventional installation directories before declaring it missing. Distinguish "not found" from an exhaustive proof of absence. Do not recursively scan the user's disk.
- Full Windows lab: check `wsl --status` and `wsl --list --verbose`. For an existing container CLI, check its engine and Compose provider, not just its CLI version. Prefer the existing working engine over installing another.
- Check Ollama's CLI and the local `http://localhost:11434/api/tags` endpoint with a bounded timeout. A stopped server is not proof that Ollama is uninstalled; an empty model list is not an installation failure.
- Git, Node/npm and an optional editor may already be installed. Do not reinstall them merely because a previous session used different versions. Resolve frontend compatibility before changing Node.

Report present, missing, installed-but-unavailable and unverified items separately. Preserve command errors; do not turn permission or registry lookup failures into claims of absence.

## 4. Propose and confirm installation

Only after the user approves the exact missing packages, refresh the catalog with `& $wingetPath source update`, then verify each proposed package identity with `& $wingetPath show --id <package-id> -e --source winget`. Stop and report catalog/network errors rather than choosing a similarly named package.

Use the required subset of these commands, one at a time:

```powershell
& $wingetPath install --id EclipseAdoptium.Temurin.25.JDK -e --source winget
& $wingetPath install --id RedHat.Podman-Desktop -e --source winget
& $wingetPath install --id RedHat.Podman -e --source winget
& $wingetPath install --id Ollama.Ollama -e --source winget
```

Only the JDK is required for the default backend. WSL2, containers and Ollama are additional full-lab requirements. If WSL2 is missing, explain that the following separate Windows command requires Administrator PowerShell and may require a restart:

```powershell
wsl --install -d Ubuntu-24.04
```

Obtain approval for WSL setup separately from package installation; if elevation is unavailable, hand the command to the user and mark the step blocked. Never restart automatically. After WSL2 is ready, guide approved Podman machine/Compose configuration through Podman Desktop. Do not invent a Compose startup command: the repository's full Compose lab is still pending.

Inspect each exit code and installer output. Stop on failure or a restart requirement, explain the result and resume only after the blocker is resolved. Do not add automatic agreement-acceptance flags.

## 5. Verify and report

Ask the user to reopen PowerShell after installation if environment changes are not visible. Do not claim success solely from installer output. Recheck Java/Javac and Maven selection; for the full lab, recheck WSL2, engine/Compose and Ollama responsiveness. Missing models and unfinished container configuration remain separate blockers.

Report actual versions, what was installed, failed or unrun checks, and the next bounded setup step. Do not claim a build, full-lab acceptance or deployment unless separately executed and verified. Never update roadmap completion checkboxes based solely on tool installation.
