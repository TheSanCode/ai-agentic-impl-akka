# Autonomous Development Plan

Date: 9 October 2026

Status: Proposed execution order; not an authorization to make external changes

## Purpose and current baseline

This plan turns the verified open work in the [roadmap](../ROADMAP.md) into a bounded sequence that a coding agent can execute one task at a time. The roadmap and production requirements remain authoritative. This plan does not change phase status, close acceptance gates, approve a license, or claim work has been completed.

The current baseline is the `feature/agentic` branch: a Temurin 25 / Spring Boot backend, OIDC/JWT validation with server-configured grants, policy-gated read tools, a deterministic synthetic investigation API and bounded allowlisted process-local execution tracing. The workflow uses neither Spring AI nor Akka at runtime. Full verification passed 147 tests; the authenticated synthetic Alpha/Beta API fixture test passed 4 tests using an in-process JWKS endpoint, including from a fresh source-only copy with no `target` output. No enterprise credentials or external services were needed. See [decision 0007](../decisions/0007-process-local-investigation-workflow.md) for exact behavior and limitations.

## Execution rules

- Work only on the authorized `feature/agentic` branch. Inspect status and diffs before editing; preserve unrelated changes.
- Execute one small, reviewable task at a time. For each task, inspect current contracts, add or update focused tests, run the narrowest relevant check and then broader checks when warranted.
- Update documentation and the roadmap only from verified evidence. A plan, code presence or unchecked test is not completion evidence.
- Keep credentials, repository URLs containing tokens, and local settings outside source control. Never use shared credentials as a substitute for user-delegated access.
- Keep provider-specific code behind existing ports. Do not add dependencies or services without a concrete need and compatibility/license review.
- Do not push, merge, deploy, provision external infrastructure, enroll vendor accounts, buy licenses, send real messages, or perform protected writes without explicit authorization.
- Stop and request a decision where work requires product, legal, security, provider, data-retention, production-runtime or business-acceptance approval. Record the decision; do not infer it from technical success.
- At each task end, report changed files, exact validation results, unrun checks, blockers and the next task. Commit coherent completed work locally using the repository's `ai-agentic-testing:` convention; do not claim a commit was pushed.

## Prioritized work sequence

| Order | Task | Scope and dependencies | Completion evidence |
| --- | --- | --- | --- |
| 1 | **P1-09: Redacted execution tracing** | Add bounded, correlated lifecycle events for the existing process-local workflow. Define a small allowlist of fields and redact credentials, source passages, private model reasoning and sensitive identifiers. No model reasoning is currently produced. Keep trace storage/output bounded and preserve authorization boundaries. | Tests prove useful lifecycle correlation while asserting tokens, secrets, retrieved text and disallowed fields never appear. Existing workflow/security suites and full build pass. Document retention and process-local scope. |
| 2 | **P1-03: Reproducible local fixtures and profiles** | Exercise the current local-only synthetic Alpha/Beta setup through authenticated integration tests, using an in-process loopback JWKS fixture. This verifies the API path without adding a manual OIDC profile, containers, Keycloak, database, model or vendor dependency. | The documented four-test Maven suite passed on Temurin 25 from the working tree and a fresh source-only copy without `target` output, creating fresh synthetic identity/JWKS fixtures and proving both project scenarios plus cross-project denial. This is automated API fixture evidence, not a turnkey manual login or full lab. |
| 3 | **P1-07: Ingestion and hybrid search** | Extend the current seeded mock search behind `SearchGateway`; implement ingestion/chunk provenance, keyword and semantic/hybrid retrieval only after confirming the minimum data model and a compatible, policy-approved storage/embedding choice. Keep restricted content filtered before any model context or citation. | Versioned synthetic evaluation cases find expected evidence; restricted chunks never enter results/context; stale permissions fail closed; citations retain source/version/access metadata. Tests cover indexing, updates/deletions and project isolation. |
| 4 | **P1-09A: Browser investigation UI** | Implement the roadmap's login, authorized project selection, submission, execution list/progress/result, cancellation and reconnect screens against existing API contracts. Choose frontend/toolchain versions only after compatibility review. No approval or business-review UI in this task. | Browser tests cover authenticated success, cross-project denial, safe rendering, cancellation/reconnect, expired authentication and keyboard-accessible primary controls. |
| 5 | **P1-01/P1-08 runtime objective decision and proof** | Before claiming the intended Phase 1 lab complete, resolve the technical-design requirement for an Akka adapter demonstrating typed request/reply, asynchronous completion, deadline and cancellation. Existing compatibility probes are not proof that the application workflow uses Akka. Use Akka only within the recorded development/non-production boundary; production license remains a separate open decision. If the requirement or scope is to change, obtain approval and document it instead of silently substituting a runtime. | Either the agreed Akka learning objective is demonstrated with tests in the application boundary, or an authorized scope decision explicitly records the deferred objective. No claim of production licensing follows from the test. |
| 6 | **P1-10: Model evaluation** | Start only after an authorized model/provider and weights are selected, license and data-use terms reviewed, and local hardware/resources assessed. Keep evaluation isolated and use synthetic inputs. | Versioned evaluation records exact model/weight identifiers, prompts/configuration, cases, criteria and actual results; includes tool/structured-output, grounding, refusal and negative security cases. No model quality claim without agreed thresholds. |
| 7 | **P1-11: Reproducible lab and Phase 1 acceptance** | Consolidate setup/start/seed/demo/stop instructions after infrastructure, retrieval, API, UI and the runtime objective are resolved. Run from a clean checkout. Do not treat the health endpoint or API-only test as full lab acceptance. | Another developer reproduces the supported browser investigation with synthetic data. Record exact commands and outputs; Phase 1 remains In progress until every mandatory roadmap exit criterion is evidenced. |
| 8 | **Phase 2: Durable specialist execution and recovery** | Begin only after Phase 1 exit criteria pass and state ownership, persistence technology/plugin, retention/deletion, queue and recovery objectives are decided. Separate Knowledge and Observability specialists; define versioned typed communication; persist accepted work/checkpoints/waits and recover safely. Revalidate identity and membership across restart and replicas. | Restart, duplicate, stale-owner, revocation, cancellation, bounded-queue and partial-failure tests pass. Demonstrate recovery without replaying completed effects. Do not add protected writes in this phase. |
| 9 | **Phase 3: Jira and code proposals** | After Phase 2, implement read-only ticket/repository context and patch proposals. Obtain sandbox threat-model approval before executing repository tests; keep network, secrets and host access restricted. Draft PR creation remains a separately authorized capability. | Tests bind proposals to ticket and base commit, reject stale inputs, and show actual sandbox commands/results. Unauthorized writes and deployment remain impossible. |
| 10 | **Phase 4: Approval-gated operations** | Requires explicit authorization policy, exact-target contract, identity/source capability and a disposable local workload. Implement approval, expiry, revocation, safety checks, idempotency/reconciliation and recovery before any restart action. | Negative tests show stale targets, changed parameters, expired/revoked approval and duplicates cannot trigger the operation. Positive execution occurs only in an explicitly authorized disposable environment. |
| 11 | **Phase 5: Technical and business verification** | Add evidence-based runbook checks and separate technical outcomes from human business acceptance. Agents cannot approve, close or manufacture business acceptance. | Tests cover pass/fail/inconclusive, missing evidence, changed deployment version, and authenticated human decision gates. |
| 12 | **Phase 6: Real integrations and production readiness** | Each connector requires an approved capability/OBO matrix, vendor-specific consent/revocation tests and explicit data/retention policy. Messaging requires provider approval. AKS work requires approved images, network, identity, storage, security and deployment authorization. | Per-integration security, capacity, recovery, licensing, backup/restore, release/rollback and operational acceptance evidence. Deployment is a separate explicitly authorized task. |

## Human decision gates

These decisions must not be made by an autonomous implementation step:

- Confirm requirements, roles, source/OBO capability matrix, and whether the Phase 1 Akka-runtime learning objective is mandatory for this lab.
- Keep production Akka licensing open. Development/non-production permission and repository access do not grant production rights; runtime-key handling requires an approved secret-store policy.
- Approve any model/provider, weights, data-use terms, evaluation thresholds and hardware assumptions before real-model evaluation.
- Select and approve storage, persistence ownership/plugin, retention/deletion, audit access, recovery objectives and multi-instance revocation behavior before durable execution work.
- Approve real-source credentials/consent, provider contracts, messaging accounts, external writes, base images, cloud resources and deployment separately.
- Assign authorized human reviewers for protected actions and business acceptance; agent output is never approval.

No human decision should be requested merely to perform isolated tests using synthetic data and already-approved repository code. Conversely, passing those tests does not approve the decisions above.

## Next single task

P1-09 tracing and the scoped P1-03 synthetic Alpha/Beta fixture setup are implemented and verified. The next planned item, P1-07 ingestion/hybrid search, requires agreement on the minimal content/provenance model and compatible, policy-approved storage/embedding choices before implementation. Do not choose a production data store, embedding model or retention policy autonomously.
