# agenticawithakka

A platform for experimenting with agents, reusable skills, controlled tools, knowledge retrieval and delegated access to platform sources, with a path from local development to production operation.

## Production requirements

Read the [production requirements](docs/requirements/agenticawithakka-production-requirements.md) for the proposed scope, functional behavior, security controls, operational targets and acceptance scenarios.

The requirements are version 0.3 and remain a draft for review. Technology choices are replaceable and will be recorded in the technical design.

## Delivery sequence

1. Agree the production requirements and open decisions.
2. Create a technical design traced to requirement IDs.
3. Create implementation instructions and acceptance checks.
4. Build and validate the local agent lab.
5. Verify real source integrations and complete production hardening.

## Planned capabilities

- Coordinating and specialist agents with structured communication.
- Versioned skills and typed tools with authorization checks.
- Keyword, semantic and hybrid knowledge retrieval.
- Delegated user access to supported platform sources.
- Human approval for protected actions.
- Durable execution, audit trails and operational observability.
- Approval-gated pod restarts with target validation, bounded attempts and recovery checks.
- Jira-linked code-change proposals with reviewable patches and test evidence.
- An admin eagle view of issue impact, timelines, evidence, agent work, approvals and outcomes within authorized access.

## Post change verification

Changes are verified against a plan derived from runbooks, Jira acceptance criteria and recent change history. After deployment and any required restart, the application polls authorized logs, metrics and health checks within a bounded window to confirm positive expected behavior and detect regressions. A restart or absence of errors alone does not prove success.

Business functionality changes also require manual acceptance by an authorized business reviewer. A change remains open until technical checks pass and business validation is approved or explicitly classified as not required. The admin issue view shows verification evidence and both gate statuses. Failed or inconclusive checks and business rejection trigger escalation; rollback remains subject to authorization.

## Current status

Requirements gathering. Application implementation and final technology selection are pending.

