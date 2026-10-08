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

## WhatsApp Telegram and SMS

Linked users can start investigations, check progress and cancel permitted work through WhatsApp, Telegram or SMS. All channels use the same project permissions and delegated source access as the browser.

Unlinked users must securely link their account first. Login, MFA, protected approvals and business acceptance use an authenticated browser handoff by default. Outgoing messages contain redacted summaries and secure result links. Real providers and channel policies remain to be selected; delivery is planned, not implemented.

## Background tasks and agent loops

Investigations run asynchronously and return an execution ID, so users can close the browser and check progress later. Agents repeat reasoning, authorized tool calls and result evaluation within step, time, token and retry limits. They pause for authentication, approvals or business review rather than holding a thread open.

Phase 1 demonstrates this while the application stays running. Phase 2 adds persisted checkpoints, durable waits, scheduled wake-ups and restart-safe recovery. Authorization is rechecked during execution; background operation does not grant indefinite access or authorize recurring unattended work. See requirements Section 20.

## Access and control

- Agents use reusable skills and controlled tools.
- Each user sees only authorized project and source information. Admin status does not bypass source permissions.
- Interactive source calls act on behalf of the user using the source's supported delegated authentication. Unsupported sources cannot silently use shared accounts.
- Authentication, authorization, search and vector storage use open-source components. Other licenses and exceptions require review.
- Execution state survives application restarts. Audit records show who requested, approved and performed actions.

## Production requirements

Read the [production requirements](docs/requirements/agenticawithakka-production-requirements.md) for detailed rules, acceptance tests and open decisions. The current requirements are **version 0.5**, a draft for review.

Section 16 covers pod restarts and Jira code proposals. Section 17 covers the admin eagle view. Section 19 covers technical verification and manual business acceptance.

## Agentic design and phased plan

Read the [agentic design and phased delivery plan](docs/design/agentic-design-and-phased-plan.md) for agent responsibilities, communication, shared services, workflows and phase exit gates.

The proposed full design uses **six AI agent roles**: Coordinator, Knowledge, Observability, Change Analysis, Operations and Verification. Start with two roles in the local lab and split them as capabilities grow. The admin eagle view is a platform feature built from execution records; business validation remains a human decision.

## Technical design

Read the [proposed technical design](docs/design/technical-design.md) for stack candidates, agent contracts, persistence, delegated access, approvals, retrieval, restart safety and verification. Unresolved choices remain explicit; implementation instructions are linked below.

## Implementation instructions

Start with the [Phase 1 implementation instructions](docs/implementation/phase-1-instructions.md): ordered tasks for the two-agent local lab, identity and tool controls, synthetic fixtures, APIs and acceptance tests. The instructions are documented; implementation remains pending.

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

Documentation review completed with corrections to access-expiry handling, historical evidence authorization and change-verification states. Read the [documentation review](docs/reviews/documentation-review.md) for findings and open decisions. Phase 0 remains In progress; implementation and deployment have not started.
