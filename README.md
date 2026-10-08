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

## Access and control

- Agents use reusable skills and controlled tools.
- Each user sees only authorized project and source information. Admin status does not bypass source permissions.
- Interactive source calls act on behalf of the user using the source's supported delegated authentication. Unsupported sources cannot silently use shared accounts.
- Authentication, authorization, search and vector storage use open-source components. Other licenses and exceptions require review.
- Execution state survives application restarts. Audit records show who requested, approved and performed actions.

## Production requirements

Read the [production requirements](docs/requirements/agenticawithakka-production-requirements.md) for detailed rules, acceptance tests and open decisions. The current requirements are **version 0.3**, a draft for review.

Section 16 covers pod restarts and Jira code proposals. Section 17 covers the admin eagle view. Section 19 covers technical verification and manual business acceptance.

## Delivery sequence

1. Agree requirements and open decisions.
2. Write the technical design.
3. Write implementation instructions and tests.
4. Build a local lab with synthetic data and mock sources.
5. Verify real integrations and complete production checks.

## Current status

Requirements gathering. Application code and the final technology stack have not been implemented or selected.
