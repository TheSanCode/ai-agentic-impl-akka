# Create and start a Podman machine on Windows

Use this guide after Podman Desktop and the Podman CLI are installed and WSL2 can run. Creating a machine downloads a Linux image and allocates local disk and memory. Perform these steps only when those local changes are approved.

Your `Ubuntu-24.04` distribution is separate from the Podman machine. Installing or starting Ubuntu does not create a Podman engine.

## 1. Check the prerequisites

Run in PowerShell:

```powershell
wsl --list --verbose
wsl -d Ubuntu-24.04 -- uname -r
podman --version
podman machine list
```

Ubuntu should show version `2`, and `uname -r` should return a Linux kernel version. `Stopped` in the distribution list is normal when Ubuntu is idle.

If a Podman machine already exists, use it instead of creating a duplicate. If it is stopped, start it through Podman Desktop.

## 2. Open Podman Desktop

1. Open the Windows Start menu.
2. Search for **Podman Desktop** and launch it.
3. Open the Podman onboarding/setup flow. If onboarding is already complete, open **Settings > Resources** and find Podman. Labels can vary by Desktop version.
4. Choose the action to create a Podman machine.

If Podman Desktop cannot detect the installed CLI, reopen the application after installation. Do not install a second container engine to bypass the problem.

## 3. Create the machine

1. Select the **WSL** provider if a provider choice is shown.
2. Review the machine name and any CPU, memory and disk options offered. Use values appropriate for the available hardware; this guide does not establish the full lab's resource budget.
3. Confirm creation and wait for the image download and initialization to finish.
4. If creation reports an error, retain the full error and resolve it before retrying. Do not delete an existing machine as a troubleshooting shortcut.

Administrator approval or a restart may be required for Windows feature changes. Do not restart automatically.

## 4. Start the machine

In Podman Desktop, select the created machine and choose **Start** if it is not already running. Wait until Desktop reports that Podman is running.

## 5. Verify the engine and Compose

Run in PowerShell:

```powershell
podman machine list
podman system connection list
podman info
podman compose version
```

Expected results:

- A machine is listed and marked as running.
- A connection for the machine is listed.
- `podman info` returns engine details without a socket or connection error.
- `podman compose version` reports the configured provider's version.

If no machine or connection is listed, machine creation has not completed. If the machine is running but connection errors persist, inspect the Podman Desktop error details and connection configuration rather than creating another machine.

If the engine works but Compose reports a missing provider, use Podman Desktop's Compose setup, review and approve any installation it proposes, and repeat `podman compose version`. An engine connection failure alone does not prove that Compose is missing.

## Scope and next step

These checks establish local engine and Compose availability only. They do not run application tests, prove database readiness or complete full-lab acceptance. The repository's full Compose lab remains pending; do not invent a Compose startup command.

Return to the [local prerequisites](local-prerequisites.md) for remaining model and lab requirements.

## References

- [Podman Desktop: Podman on Windows](https://podman-desktop.io/docs/installation/windows-install)
- [Podman Desktop: Compose](https://podman-desktop.io/docs/compose)
