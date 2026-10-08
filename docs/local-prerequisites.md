# Windows Local Installation Prerequisites

Last updated: 7 October 2026
Target: Windows 11 x64 with WSL2, for the AgenticaWithAkka local lab.

## Current setup context

This guide installs/checks development tools. Application code, Compose configuration and a runnable setup guide remain to be implemented. AKS and an Azure subscription are not needed for the initial synthetic local lab.

## Current Installation Status

| Tool | Status / Version |
| --- | --- |
| WinGet | v1.29.380 |
| Git | 2.55.0.windows.4 |
| Java JDK | 21.0.8 |
| Node.js | v24.10.0 (npm 11.6.1) |
| VS Code | 1.138.0 |
| Docker Desktop | Not installed |
| Ollama | 0.35.1 (service not running) |
| WSL2 | Not installed |

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
winget list --id Docker.DockerDesktop -e
winget list --id Ollama.Ollama -e
winget list --id Microsoft.VisualStudioCode -e
```

List output can show installed and available versions. A manually installed or portable tool may not be recognized under this exact package ID; use the version commands below before assuming it is absent.

## 3 Install missing tools or update existing ones

Run individually so you can inspect errors or prompts. These commands use the latest available version in each selected package family; they do not pin the project's final tested dependency set.

| Tool | Install command |
| --- | --- |
| Git | `winget install --id Git.Git -e --source winget` |
| Java 25 JDK | `winget install --id EclipseAdoptium.Temurin.25.JDK -e --source winget` |
| Node.js LTS with npm | `winget install --id OpenJS.NodeJS.LTS -e --source winget` |
| Docker Desktop | `winget install --id Docker.DockerDesktop -e --source winget` |
| Ollama | `winget install --id Ollama.Ollama -e --source winget` |
| VS Code | `winget install --id Microsoft.VisualStudioCode -e --source winget` |

WinGet install normally attempts an upgrade when the package is already installed. For an explicit update, use `winget upgrade --id <package-id> -e --source winget`. No available upgrade means no update was found, not an installation failure. Inspect other error messages rather than treating every nonzero exit as package absence. Do not automatically upgrade all unrelated software.

Java 25 is the proposed LTS baseline; this package installs patches within that major, not the newest Java major. Verify the final Java/Spring/Akka combination before building. Match Node.js to the chosen Angular release's supported Node range; latest LTS is not automatically compatible with every existing project.

VS Code is optional if another suitable IDE is installed. Docker Desktop licensing may require review for organizational use. Verify package identities with `winget show --id <package-id> -e --source winget` if catalog resolution changes.

## 4 Check and configure WSL2

Check in PowerShell:

```powershell
wsl --status
wsl --list --verbose
```

If WSL is missing, run in Administrator PowerShell:

```powershell
wsl --install
```

If WSL is already installed:

```powershell
wsl --update
```

Restart if Windows requests it and finish the distribution's first-run setup. Verify the intended distribution uses WSL2. Configure Docker Desktop to use the WSL2 backend. Hardware virtualization must be enabled if WSL/Docker reports it unavailable.

## 5 Verify after installation

Reopen PowerShell to pick up PATH changes:

```powershell
git --version
java -version
javac -version
node --version
npm --version
docker --version
docker compose version
docker info
ollama --version
code --version
```

Docker Desktop must be running for `docker info`; the CLI version alone does not prove the engine works. If PowerShell blocks npm's script wrapper, try `npm.cmd --version` rather than broadly disabling execution policy.

If Java reports an older JDK, inspect `where.exe java`, `where.exe javac` and `$env:JAVA_HOME`. Select the intended JDK in project/IDE configuration; do not blindly overwrite a system configuration used by other projects.

Ollama installation does not install a chat or embedding model. Model selection, weight licenses, downloads and resource budgets are separate bootstrap tasks.

## 6 Tools and services for later phases

| Item | Needed when |
| --- | --- |
| kubectl, kind and Helm | Local Kubernetes manifests and pod-restart experiments |
| Azure CLI and approved cluster/registry access | Authorized AKS deployment preparation |
| Messaging provider accounts | Real WhatsApp, Telegram and SMS integration, after mock testing |
| Approved base-image registry access | Building with organization-controlled base images |

Do not download unapproved replacement images if corporate base-image access is unavailable. Record the blocker or use an explicitly documented local-only candidate.

No separate host installation is planned for PostgreSQL, pgvector or Keycloak; run them through the future local Compose setup. Maven Wrapper belongs in the repository. Angular CLI/build dependencies should be project-local and lockfile-controlled.

## 7 Readiness checklist

- [ ] Git and chosen IDE work.
- [ ] Java/Javac and frontend toolchain match the final recorded dependency decisions.
- [ ] WSL2 and Docker engine work.
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
- [Angular version compatibility](https://angular.dev/reference/versions)
