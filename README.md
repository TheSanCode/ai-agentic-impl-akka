# AgenticaWithAkka

AI agents work together to investigate issues, suggest fixes and check whether the fixes worked. All technology choices remain replaceable until the technical design is agreed.

## What the application does

1. Reads authorized tickets, runbooks, logs, monitoring data and recent changes to understand an issue.
2. Assigns tasks to specialist agents and combines their findings.
3. Suggests an operational fix or a code patch linked to a Jira ticket, with an explanation and test evidence.
4. Gets approval before protected actions. Shows the exact target and proposed change.
5. Restarts a pod when approved, checks safety and verifies recovery. Prevents repeated restart loops.
6. After deployment and any required restart, polls logs, metrics and health checks against the runbook and acceptance criteria. A restart or absence of errors alone does not prove success.
7. Requires manual business validation when business functionality changes, in addition to technical checks.
8. Keeps the change open until all required checks pass. Failures or unclear results require follow-up; rollback needs authorization.
9. Gives admins an eagle view of issue impact, affected services, timelines, evidence, agent work, code proposals, approvals and validation results.

## Browser web application

The target is an authenticated browser interface on desktop and mobile. The current local investigation slice exposes authenticated APIs only; a browser interface, project-selection screens and accessibility checks are not implemented.

Later workflow phases add protected-action approvals, manual business validation and the admin eagle view. Messaging channels complement the planned interface.

## WhatsApp Telegram and SMS

Linked users can start investigations, check progress and cancel permitted work through WhatsApp, Telegram or SMS. All channels use the same project permissions and delegated source access as the browser.

Unlinked users must securely link their account first. Login, MFA, protected approvals and business acceptance use an authenticated browser handoff by default. Outgoing messages contain redacted summaries and secure result links. Real providers and channel policies remain to be selected; delivery is planned, not implemented.

## Background tasks and agent loops

The current Phase 1 API runs a deterministic, read-only Coordinator/Investigation sequence over synthetic knowledge, logs and health adapters. It returns an execution ID; authorized status, result, cancellation and same-subject reauthentication/resume routes work only while the same process remains running. Tool steps, elapsed time, active work, queue size, retained execution count and lifecycle trace events are bounded. Traces use an allowlisted metadata schema, are process-local, and have no public retrieval route; they are not an audit log. The workflow does not call a model or run Akka actors.

Execution state, checkpoints and revocation are process-local. A restart loses all executions; another application instance does not share or observe this state. Phase 2 must add persisted checkpoints, durable waits, scheduled wake-ups and restart-safe recovery before those capabilities can be claimed. Authorization is rechecked during execution and before stored evidence is returned; background operation does not grant indefinite access or authorize recurring unattended work. See requirements Section 20.

## Access and control

- Agents use reusable skills and controlled tools.
- Each user sees only authorized project and source information. Admin status does not bypass source permissions.
- Interactive source calls act on behalf of the user using the source's supported delegated authentication. Unsupported sources cannot silently use shared accounts.
- Authentication, authorization, search and vector storage use open-source components. Other licenses and exceptions require review.
- Restart-safe execution state and audit records are production requirements, not capabilities of the current local process-only implementation.

## Production requirements

Read the [production requirements](docs/requirements/agenticawithakka-production-requirements.md) for detailed rules, acceptance tests and open decisions. The current requirements are **version 0.7**, a draft for review.

Section 16 covers pod restarts and Jira code proposals. Section 17 covers the admin eagle view. Section 19 covers technical verification and manual business acceptance.

## Agentic design and phased plan

Read the [agentic design and phased delivery plan](docs/design/agentic-design-and-phased-plan.md) for agent responsibilities, communication, shared services, workflows and phase exit gates.

The proposed full design uses **six AI agent roles**: Coordinator, Knowledge, Observability, Change Analysis, Operations and Verification. Start with two roles in the local lab and split them as capabilities grow. The admin eagle view is a platform feature built from execution records; business validation remains a human decision.

## Technical design

Read the [proposed technical design](docs/design/technical-design.md) for stack candidates, agent contracts, persistence, delegated access, approvals, retrieval, restart safety and verification. Unresolved choices remain explicit; implementation instructions are linked below.

## AKS deployment layout

AKS is the target hosting platform. UI and API use separate Deployments and internal Services; durable workers are separated from Phase 2. Messaging adapters initially run with the API and use controlled webhook routes. No separate pod is required for every access channel or logical agent.

All custom images use approved base images, multi-stage builds and pinned release digests. Exact base-image catalog, ingress/gateway, registry and cluster settings remain to confirm. Browser traffic routes to UI and API under one application origin; workers and data stores remain internal. See technical design Section 19. No AKS resources have been deployed.

## Technology stack

See the [recommended technology stack](docs/design/technology-stack.md): Angular browser UI, Java/Spring Boot/Spring AI, Akka, Keycloak, PostgreSQL/pgvector, local inference, durable scheduling and replaceable Telegram/WhatsApp/SMS adapters. Exact versions and provider choices require validation; no service purchase or deployment is implied.

## Local installation prerequisites

Start with the [Windows backend scaffold guide](docs/implementation/backend-scaffold.md) for verified JDK 25 build and health-check commands. The scaffold needs no containers or models. The [Windows local prerequisites](docs/local-prerequisites.md) cover tools for the later full lab; Compose remains pending.

### Windows installation with WinGet

Check WinGet in PowerShell before installing anything:

```powershell
Get-Command winget.exe
winget --version
winget source update
```

If WinGet is missing, install or update Microsoft's **App Installer** using the [official WinGet installation guidance](https://learn.microsoft.com/en-us/windows/package-manager/winget/#install-winget), then reopen PowerShell. Do not download an installer from an unverified mirror.

Install only missing prerequisites, one command at a time. **JDK 25 is required for the backend**:

```powershell
winget install --id EclipseAdoptium.Temurin.25.JDK -e --source winget
```

For the **full local lab**, install WSL2 first in **Administrator PowerShell**:

```powershell
wsl --install -d Ubuntu-24.04
```

Restart if prompted and complete Ubuntu's first-run setup. Then install the additional lab tools:

```powershell
winget install --id RedHat.Podman-Desktop -e --source winget
winget install --id RedHat.Podman -e --source winget
winget install --id Ollama.Ollama -e --source winget
```

Configure the Podman machine and Compose provider through Podman Desktop. Do not install a second container engine if an existing supported engine already works. Ollama models require a separate, approved download; installing Ollama alone does not make the retrieval profile ready.

Reopen PowerShell, ensure `JAVA_HOME` points to JDK 25 and its `bin` directory is on `PATH`, then verify:

```powershell
java -version
javac -version
.\mvnw.cmd --version
wsl --list --verbose
podman info
podman compose version
ollama --version
```

Maven must report Java 25. No standalone Maven or global Angular CLI installation is needed. Node.js/npm are needed for later frontend work; check existing versions before installing them. The [local prerequisite setup skill](.github/skills/local-prerequisites/SKILL.md) detects the host OS, locates WinGet on Windows and asks for confirmation before installing missing tools. Non-Windows installation is not implemented in this skill yet.

## Implementation instructions

Follow The [Phase 1 implementation instructions](docs/implementation/phase-1-instructions.md) for remaining local lab tasks. The backend scaffold, local identity/policy/mock-source adapters, OIDC/JWT verification and process-local deterministic read-only investigation API are implemented and tested. P1-07 has a real Ollama embedding model and an opt-in PostgreSQL/pgvector profile with an ownership-guarded synthetic-only reset; its database-backed ingestion/retrieval/revocation/restart test remains blocked because no local PostgreSQL-capable engine is available. Model-driven agent behavior, durable recovery, browser UI, real-source OBO, production source adapters and full lab acceptance remain pending. See [decision 0007](docs/decisions/0007-process-local-investigation-workflow.md) for workflow boundaries, [decision 0008](docs/decisions/0008-synthetic-postgres-hybrid-retrieval.md) for retrieval status, the [OIDC/JWT decision](docs/decisions/0006-oidc-jwt-authentication.md), the [dependency decision](docs/decisions/0001-backend-scaffold.md) for verified scaffold versions, and the [isolated Spring AI/Akka probes](docs/decisions/0002-integration-probes.md) for compatibility evidence and the still-open Akka production-license decision.

## Roadmap

See the [project roadmap](docs/ROADMAP.md) for phase status, deliverables, dependencies and completion criteria. Maintain it with the reusable `project-roadmap` skill using verified progress.

## Roadmap update instructions for agents

Use the `project-roadmap` skill when it is available. If it is unavailable, follow the steps below directly. Treat this as a documentation workflow; it does not authorize deployment, issue closure or business approval.

1. **Confirm the target branch.** Use the branch specified by the user; for this workstream, use `feature/agentic`. Read applicable `AGENTS.md` instructions and check for existing changes before editing.
2. **Read current sources.** Read this README, [production requirements](docs/requirements/agenticawithakka-production-requirements.md), [agentic design](docs/design/agentic-design-and-phased-plan.md) and [roadmap](docs/ROADMAP.md). Use current files rather than remembered versions.
3. **Gather progress evidence.** Inspect relevant code, commits, pull requests, test results, deployment records and review decisions. Link actual evidence where available. A code commit alone does not prove tests passed, deployment succeeded or business acceptance occurred.
4. **Update task checkboxes.** Check a task only when its deliverable is demonstrably complete. Preserve existing phase IDs, dependencies and valid evidence. Record missing evidence, blockers and unresolved decisions explicitly.
5. **Update phase status.** Use Planned, In progress, Blocked or Complete. Mark a phase Complete only after all mandatory deliverables and exit criteria are met. Record material regressions or reopened work instead of retaining unsupported completion.
6. **Respect validation gates.** Keep technical verification and manual business acceptance separate. Never infer business approval from logs, health checks or a restart. Keep protected actions, rollback and source access subject to their existing authorization rules.
7. **Keep the document consistent.** Update the last-reviewed date using the project/user timezone. Keep the phase table, detailed phases and Mermaid diagram aligned. Do not invent owners, dates, issues, completion percentages or estimates.
8. **Check the result.** Verify relative links, Markdown checkboxes, Mermaid syntax, dependencies and status consistency. Preserve unrelated README content. If requirements and design conflict, report the conflict rather than silently changing scope.
9. **Save through the authorized workflow.** Read current file versions or SHAs before replacing files. Commit to the authorized branch or prepare the requested pull request. Do not merge, deploy, assign people or create issues merely to update the roadmap.
10. **Report the update.** State which phases or tasks changed, the supporting evidence, remaining blockers and the saved roadmap location. If nothing is verified complete, leave the relevant tasks unchecked.

Example request:

> Use project-roadmap to update docs/ROADMAP.md on feature/agentic from current code and validation evidence. Preserve incomplete technical and business validation gates and summarize the changes.

## Delivery sequence

1. Agree requirements and open decisions.
2. Write the technical design.
3. Write implementation instructions and tests.
4. Build a local lab with synthetic data and mock sources.
5. Verify real integrations and complete production checks.

## Current status

Documentation review completed with corrections to access-expiry handling, historical evidence authorization and change-verification states. Read the [documentation review](docs/reviews/documentation-review.md) for findings and open decisions. Phases 0 and 1 remain In progress. The backend scaffold, mock identity/policy/delegated-source adapters, OIDC/JWT authentication and a process-local read-only investigation API are implemented and tested. The workflow uses deterministic synthetic evidence rather than AI inference; it has no durable recovery, multi-instance state, UI or full-lab setup. Real-source OBO, production Akka licensing/runtime-key decisions and full lab acceptance remain open. No deployment has occurred.
