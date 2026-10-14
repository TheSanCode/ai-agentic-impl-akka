# Windows Local Installation Prerequisites

Last updated: 14 October 2026

Target: Windows 11 x64 with WSL2, for the AgenticaWithAkka local lab.

## Current setup context

This guide checks development tools for the full lab. The [backend scaffold guide](implementation/backend-scaffold.md) now provides verified JDK 25 build and health-check commands without containers or models; Compose and full lab setup remain pending. AKS and an Azure subscription are not needed for the initial synthetic local lab. Prefer open-source components where practical: Eclipse Temurin, Node.js, Git, Ollama and Podman. VS Code is optional; VSCodium is an open-source alternative.

## Current Installation Status

The table describes the current Windows checkout, checked on 13 October 2026 with WSL setup observations updated on 14 October. Earlier machine observations are not evidence of this machine's readiness. An installed runtime is not necessarily selected by an already-open shell.

| Tool | Observed status / version | Next action |
| --- | --- | --- |
| WinGet | v1.29.380 | Available; `winget source update` completed successfully. |
| Git | 2.55.0.windows.5 | `git --version` verified. |
| Java JDK | Temurin OpenJDK 25.0.4.1+1 LTS installed per-user through WinGet; Java and Javac 25.0.4.1 verified. User `JAVA_HOME` and user PATH are configured by the installer. | Reopen PowerShell to pick up environment changes. Maven Wrapper selected Java 25 when the current process environment was explicitly refreshed. |
| Maven | Repository wrapper runs Apache Maven 3.9.11 on Java 25.0.4.1. | No standalone installation needed. Application build/tests were not run during this installation session. |
| Node.js | PATH selects Node.js 24.19.0 and npm 11.17.0. | Confirm compatibility with the selected Angular release before frontend work. |
| VS Code | 1.141.1 | `code --version` verified. VSCodium is an optional alternative. |
| Container runtime | Podman Desktop 1.29.3 installed; installer success and installed-application record confirmed. Podman and Docker CLIs are not on the current PATH; engine and Compose readiness are unverified. | After WSL2 setup, install/configure the Podman engine and Compose provider. Desktop installation alone does not provide a verified engine. The pgvector acceptance test remains blocked. |
| Ollama | WinGet installation of 0.40.3 started on 14 October; completion remains unconfirmed. No model availability verified on this machine. | Wait for the running installer result; do not launch a duplicate. Separately approve the selected embedding model download and verify service, digest and dimensions. |
| WSL2 | WSL 3.0.3.0 installed, default version 2. No distribution registered. `wsl --status` reports WSL2 cannot start because virtualization is unavailable. A Windows component-servicing restart is pending. | Restart Windows manually before rechecking. If still blocked, an administrator must check Virtual Machine Platform and firmware/host virtualization before Ubuntu/Podman setup. |

The user authorized resuming installation with administrator assistance on 14 October. WSL installation returned zero, but runtime checks failed; WSL2/Ubuntu and Podman readiness are not established. Further dependent setup is blocked pending a manual restart and virtualization recheck. No automatic restart or model download is authorized. Remaining full-lab order: **resolve WSL2 virtualization/restart → Ubuntu and Podman engine/Compose configuration → verify Ollama installation and approved model → final service checks**. JDK 25 and Maven version checks passed; no application build or full-lab acceptance was performed. The previously selected synthetic embedding model remains `nomic-embed-text:latest`, digest `0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`, 768 dimensions; prior model evidence does not establish availability on this machine.

## 1 Check WinGet and refresh its catalog

Run in PowerShell:

```powershell
winget --version
winget source update
```

If WinGet is missing, install/update Microsoft's App Installer through the supported Windows setup route. Do not infer package availability from the reported WinGet version alone.

## 2 Check each prerequisite

```powershell
winget list --id Git.Git -e
winget list --id EclipseAdoptium.Temurin.25.JDK -e
winget list --id OpenJS.NodeJS.LTS -e
winget list --id RedHat.Podman -e
winget list --id RedHat.Podman-Desktop -e
winget list --id Ollama.Ollama -e
winget list --id VSCodium.VSCodium -e
winget list --id Microsoft.VisualStudioCode -e
```

List output can show installed and available versions. A manually installed or portable tool may not be recognized under an exact package ID; use the version commands below before assuming it is absent.

## 3 Install missing tools or update existing ones

Run individually so you can inspect errors or prompts. These commands use the latest available version in each selected package family; they do not pin the project's final tested dependency set.

| Tool | Install command |
| --- | --- |
| Git | `winget install --id Git.Git -e --source winget` |
| Java 25 JDK (open-source Temurin) | `winget install --id EclipseAdoptium.Temurin.25.JDK -e --source winget` |
| Node.js LTS with npm | `winget install --id OpenJS.NodeJS.LTS -e --source winget` |
| Podman Desktop (open source) | `winget install --id RedHat.Podman-Desktop -e --source winget` |
| Podman engine (open source) | Install/configure through Podman Desktop after WSL2 is ready; see the Podman setup guide. |
| Ollama | `winget install --id Ollama.Ollama -e --source winget` |
| VSCodium (optional open-source editor) | `winget install --id VSCodium.VSCodium -e --source winget` |
| VS Code (optional alternative) | `winget install --id Microsoft.VisualStudioCode -e --source winget` |

WinGet install normally attempts an upgrade when the package is already installed. For an explicit update, use `winget upgrade --id <package-id> -e --source winget`. No available upgrade means no update was found, not an installation failure. Inspect other error messages rather than treating every nonzero exit as package absence. Do not automatically upgrade unrelated software.

Temurin is an open-source OpenJDK distribution. On this machine WinGet verified the installer hash and installed JDK 25 per-user. The first attempt was cancelled (MSI 1602); an explicitly approved interactive retry succeeded. Node.js 24.19.0 and npm 11.17.0 are already selected by PATH. Resolve active executables before building and match Node.js to the selected Angular release's supported range.

Podman and Podman Desktop are open-source alternatives to Docker Desktop. Configure Podman Desktop to create its Podman machine after WSL2 is installed, and verify the Compose provider before using Compose files. Docker Desktop remains an optional alternative; review its licensing terms for organizational use. Verify package identities with `winget show --id <package-id> -e --source winget` if catalog resolution changes.

## 4 Check and configure WSL2

Check in PowerShell:

```powershell
wsl --status
wsl --list --verbose
```

If WSL is missing, run in Administrator PowerShell:

```powershell
wsl --install -d Ubuntu-24.04
```

If WSL is already installed, update it with:

```powershell
wsl --update
```

Restart if Windows requests it and finish the distribution's first-run setup. Verify the intended distribution uses WSL2. Then install Podman Desktop, create its Podman machine and verify that the engine is available. Hardware virtualization must be enabled if WSL or Podman reports it unavailable. Do not attempt this step from a non-elevated shell.

## 5 Verify after installation

Reopen PowerShell to pick up PATH changes:

```powershell
git --version
java -version
javac -version
node --version
npm --version
podman --version
podman machine list
podman info
podman compose version
ollama --version
code --version
```

The installed JDK is under `%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-25.0.4.101-hotspot`. User `JAVA_HOME` points there. An existing terminal or assistant process may retain the old environment: reopen it or explicitly select this JDK for that process before running Maven. Full-path Java and Javac checks and Maven's Java 25 selection passed.

Use `where.exe node` if `node --version` does not show the intended Node.js LTS. Current checks select 24.19.0; do not remove or replace runtimes used by other projects. If PowerShell blocks npm's script wrapper, try `npm.cmd --version` rather than broadly disabling execution policy.

Podman must be running for `podman info`; the CLI version alone does not prove the engine works. `podman compose` also requires a supported Compose provider. Ollama installation does not install or start a chat or embedding model. Model selection, weight licenses, downloads and resource budgets are separate bootstrap tasks.

## 6 Tools and services for later phases

| Item | Needed when |
| --- | --- |
| kubectl, kind and Helm | Local Kubernetes manifests and pod-restart experiments; verify compatibility with the selected Podman machine first. |
| Azure CLI and approved cluster/registry access | Authorized AKS deployment preparation |
| Messaging provider accounts | Real WhatsApp, Telegram and SMS integration, after mock testing |
| Approved base-image registry access | Building with organization-controlled base images |

Do not download unapproved replacement images if corporate base-image access is unavailable. Record the blocker or use an explicitly documented local-only candidate.

No separate host installation is planned for PostgreSQL, pgvector or Keycloak; run them through the future local Compose setup. Maven Wrapper belongs in the repository. Angular CLI/build dependencies should be project-local and lockfile-controlled.

## 7 Readiness checklist

- [ ] Git and chosen IDE work.
- [ ] Selected Java/Javac and frontend toolchain match the final recorded dependency decisions.
- [ ] WSL2 and Podman engine work.
- [ ] Ollama works and selected models fit the available hardware.
- [ ] Required images/models can be downloaded through the local network.
- [ ] Disk space and container/model memory budgets are agreed.
- [ ] Local credentials and project configuration are separated from production.
- [ ] No real enterprise sources are required for the first synthetic scenario.

Follow [Phase 1 instructions](implementation/phase-1-instructions.md) after these checks. These checkboxes are unverified until the developer runs them.

## References

- [WinGet list](https://learn.microsoft.com/en-us/windows/package-manager/winget/list)
- [WinGet install](https://learn.microsoft.com/en-us/windows/package-manager/winget/install)
- [WinGet upgrade](https://learn.microsoft.com/en-us/windows/package-manager/winget/upgrade)
- [WSL installation](https://learn.microsoft.com/en-us/windows/wsl/install)
- [Podman Desktop on Windows](https://podman-desktop.io/docs/installation/windows-install)
- [Podman Compose](https://docs.podman.io/en/latest/markdown/podman-compose.1.html)
- [Angular version compatibility](https://angular.dev/reference/versions)
- [Eclipse Temurin](https://adoptium.net/temurin/releases/)
- [Node.js releases](https://nodejs.org/en/about/previous-releases)
