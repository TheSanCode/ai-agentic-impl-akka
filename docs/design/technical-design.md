# AgenticaWithAkka Technical Design

Version 0.1 | 7 October 2026 | Proposed implementation design

## 1 Scope and source baseline

Implement a local-first modular application with replaceable adapters, initially using Coordinator and Investigation agents. Expand to the six roles defined in the [agentic design](agentic-design-and-phased-plan.md). Follow [requirements v0.3](../requirements/agenticawithakka-production-requirements.md) and the [roadmap](../ROADMAP.md). This is a proposed design, not evidence that Phase 0 is complete.

Source baseline on feature/agentic: requirements blob `97db05215bd6bb2b9e59776d60cb0cf6dbfc23bb`, agentic design blob `516e326ff548c4fe9a51e3ac02ef6dc5c956cb45`. Requirements remain authoritative if this design conflicts with them.

## 2 Proposed technology baseline

| Concern | Proposal | Replacement boundary and unresolved checks |
| --- | --- | --- |
| Java | Java 25 LTS baseline plus a latest-GA Java compatibility profile | Do not call Java 25 the latest release. Resolve the latest GA toolchain and test Boot, AI and actor dependencies before enabling that profile. |
| API and composition | Spring Boot 4; initial compatibility candidate 4.0.8 | Use current supported patched release at implementation; verify with Spring AI BOM rather than mix untested versions. |
| Model integration | Spring AI 2.0.1 stable candidate | ModelGateway isolates framework/provider types. Milestone versions are optional experiments. |
| Agent runtime | Akka Typed, with durable orchestration introduced in Phase 2 | Resolve exact version, Java support, persistence plugin and licensing. Provide AgentRuntime interface for an alternative. |
| Local inference | Ollama with selected tool-capable chat and embedding models | Verify hardware fit and model-weight licenses. Pin model digest and embedding dimension. |
| Identity | Keycloak plus Spring Security | IdentityContext and DelegatedTokenProvider contracts. Validate specific token-exchange configuration. |
| Policy | Server-side authorization service; OPA adapter later if useful | Deny-first PolicyDecision contract; no model makes binding access decisions. |
| Business state | PostgreSQL with schema migrations | Repository interfaces; separate identity-provider database from application schema. |
| Retrieval | PostgreSQL full-text search plus pgvector initially | SearchGateway supports hybrid retrieval; optional OpenSearch profile for comparison. One vector store is sufficient. |
| Local operation | Docker Compose, with an opt-in kind cluster in Phase 4 | Pin images. Select resource budgets after measuring laptop capacity. |
| Telemetry | OpenTelemetry-compatible tracing and metrics | Mask source payloads and credentials; choose local collector/dashboard distribution later. |

Authentication, authorization, search and vector components must meet the open-source policy. Akka is source available under BSL and requires an explicit licensing decision for production. All component and model licenses belong in the implementation inventory. These proposals do not authorize a production rollout.

## 3 Application boundaries

Start with one API application and packages for domain, application, agents, security, connectors, retrieval, workflow, verification and persistence. Expose no provider types through domain contracts. Keep the code-validation sandbox and infrastructure action executor isolated from unrestricted model execution. Expand deployment units only after demonstrated scaling or trust-boundary needs.

Core ports: ModelGateway, AgentRuntime, SearchGateway, SourceConnector, DelegatedTokenProvider, PolicyDecisionService, ApprovalRepository, ExecutionRepository, ActionExecutor and VerificationScheduler. Every adapter declares capability limitations. Protocol choices such as direct APIs and MCP do not bypass authorization.

Browser login uses an OIDC authorization-code flow with PKCE through a backend session design. Use secure HTTP-only session cookies, CSRF protection for state changes and validated redirect origins. REST clients may use audience-validated access tokens. Never accept an ID token as an API credential.

## 4 Agent runtime and message contracts

Create an investigation-scoped coordinator actor when Akka is selected. Phase 1 may use a simple in-process AgentRuntime adapter. Specialist workers execute asynchronous model/tool work and return typed result messages. Do not block actor dispatchers on HTTP, database or inference calls.

| Contract | Required fields |
| --- | --- |
| TaskEnvelope | schemaVersion, executionId, taskId, correlationId, projectId, identityContextRef, deadline, agentRole, input, replyRoute |
| TaskResult | taskId, status, evidenceRefs, findings, confidence limitations, errorCode, completedAt |
| ToolRequest | executionId, taskId, registeredToolId/version, validatedArguments, idempotencyKey |
| ActionProposal | exact target, operation, immutable parameters hash, resource version, risk class, evidenceRefs, expiry |
| VerificationPlan | deployed artifact, source/runbook versions, baselineRefs, positive checks, regression checks, polling policy, businessImpact |

The server creates identityContextRef from authenticated state. Client or model input cannot replace it. Reply routes are runtime references locally and explicit serialized routing identifiers if distributed later. Do not serialize ActorRef fields as arbitrary JSON.

Enforce step, elapsed-time, token and cost budgets. Scope tool discovery to the agent and project. Ask or request/reply timeouts are explicit; late responses cannot resurrect cancelled work. A failed specialist produces an explicit partial result. Model confidence does not replace evidence.

## 5 Durable state and side effects

Persist execution states Queued, Running, AwaitingApproval, AwaitingAuthentication, AwaitingBusinessReview, Succeeded, Failed and Cancelled. Keep TechnicalVerification and BusinessValidation separate from task success. An investigation task can finish while its associated change remains open.

Suggested tables: projects, memberships, executions, tasks, evidence, proposals, approvals, action_attempts, verification_runs, verification_checks, business_reviews, skill_versions, tool_versions and audit_events. Evidence includes source ID/version, classification, timestamps, project and permission references. Credentials use a separate encrypted credential store with restricted access.

Use optimistic versions for state transitions. If Akka persistence is selected, keep its journal/snapshots authoritative for actor state and build query projections from events; do not independently overwrite the same state in tables. Use a tested outbox/inbox pattern for reliable event propagation. Projection rebuilds must be possible.

Persist intended action before execution. Generate an idempotency key bound to the action and target. When the source does not support idempotency, reconcile source state/receipts before retrying. There is no assumed exactly-once distributed execution. Never replay a protected external write merely because an actor recovered.

## 6 Delegated authentication and authorization

1. Authenticate the user and resolve current project membership.
2. Identify the connector, intended resource and operation.
3. Check application policy and source capability support.
4. Acquire a source-specific delegated credential through the connector's provider.
5. Validate permissions and execute using the intended audience/scope.
6. Record a redacted decision and result with correlation identifiers.

Keycloak token exchange demonstrates downstream audience-scoped tokens for local mock services. It does not make Keycloak tokens valid for Microsoft Graph, Jira or another external platform. Each real connector needs its supported delegated authorization, account linking, consent, refresh and challenge handling. Microsoft OBO, where used, requires the corresponding Entra flow and accepted incoming credential. Unsupported delegation disables the operation; no shared-account fallback.

Revalidate identity, access and approval on resumed tasks and before writes. Expired delegated access moves the task to AwaitingAuthentication rather than continuing under another identity. Background ingestion uses a separately approved workload identity or user-triggered delegated ingestion. Neither approach is implicitly approved by this design.

## 7 Knowledge and retrieval

Ingest synthetic documents first. Preserve source version, access metadata and links during extraction/chunking. Store vectors with embedding model ID, dimension and index version. Perform keyword and semantic searches, combine ranked results using a configurable ranking strategy, and evaluate against a fixed benchmark.

Permission enforcement precedes inclusion in model context. Apply candidate filters and a current authorization check; stale ACLs alone cannot authorize a document. Cache keys include project, user/access scope, source version and policy version. Permission changes invalidate relevant caches; unavailable permission checks fail closed under the agreed policy.

Re-embedding writes a new compatible index version; evaluate before switching and retain a rollback/rebuild procedure. PostgreSQL and OpenSearch profiles are alternatives, not mandatory duplicate stores. A large intermediate document copy needs explicit retention and access policy; direct source ingestion is allowed where feasible.

## 8 Jira code proposal workflow

Read the authorized ticket, acceptance criteria, repository mapping, base branch/commit and relevant deployment changes. Produce a structured proposal with patch, rationale, affected behavior, test plan and risk. Validate patches against the exact base; reject stale proposals or regenerate.

Run tests in an ephemeral sandbox without production secrets or host/container socket access. Default-deny network egress and bound CPU, memory, output and execution time. Record commands, exit status and test artifacts; distinguish not run from passed. Repository code and ticket text are untrusted.

Branch creation, pushing and draft PR creation require their own policy and user authorization. Merge/deployment remain separate gates. A deployed-artifact confirmation, including commit or digest, triggers verification. A PR or successful compile does not establish deployment.

## 9 Operational restart workflow

Inspect the exact cluster, namespace, workload owner, pod UID and resource version. For the first local experiment, support only explicitly allowed controller-managed workloads. Do not offer blanket access to arbitrary pods.

Check replicas/readiness, active disruptions, workload restrictions and configured cooldown. Show whether the action evicts a managed pod or performs a workload rollout. Where disruption budgets must be honored, use the appropriate eviction contract; direct deletion does not inherently enforce them. Re-read the target after approval and before execution.

The deterministic executor obtains the current delegated credential, validates immutable approval content and safety rules, performs the specific action and records the receipt. Observe replacement/readiness under a deadline. Readiness reports operational recovery only; Verification checks the intended technical effect. Escalate failures or uncertain outcomes and request authorization for any rollback.

## 10 Post change and business verification

VerificationPlan contains versioned runbook/ticket references, deployed version, affected services, baseline, positive signals, regression limits, warm-up, polling interval, observation deadline and business-impact classification. Values are configurable and remain undecided until scenario-specific agreement.

The scheduler persists next observation and checkpoints. Poll bounded time ranges in authorized sources; account for ingestion lag, cursor duplicates and clock differences. Avoid one model call per polling tick. Deterministic checks evaluate explicit signals; the agent can summarize evidence and flag ambiguity. Missing evidence yields Inconclusive, not Passed.

Record technical Pending, Running, Passed, Failed or Inconclusive. Business status is NotRequired, Pending, Approved or Rejected. A not-required classification needs an authorized rationale. An uncertain classification requires review. Business reviewers submit acceptance through an authenticated endpoint tied to deployed version and scenarios; agents cannot manufacture it.

Close a change only after technical Passed and business Approved or valid NotRequired. Relevant subsequent deployment invalidates prior verification/acceptance. Jira closure is an external action subject to authorization; local closure eligibility alone does not authorize it.

## 11 API and admin view

Proposed API contracts: create/read/cancel investigation; read task/evidence timeline; create/read action proposal; submit approval; start/read verification; submit business review; and read admin issue overview. Use project-scoped resource identifiers, pagination, optimistic concurrency and consistent errors: Denied, ConsentRequired, Unsupported, NotFound, Conflict, RateLimited and DependencyUnavailable.

The admin projection joins issue impact, service relationships, time-ordered evidence, task states, changes, approvals and verification results. Filter before returning any content. Mark freshness and partial coverage; source admin status does not derive from application admin status. Store only approved decision summaries, not private model reasoning.

## 12 Local profiles and tests

Default lab: application, PostgreSQL/pgvector, Keycloak, local inference and seeded mock sources. Separate namespaces/databases and credentials prevent local access to production. Optional search profile adds OpenSearch; optional remediation profile adds a disposable kind cluster. Hostnames, ports, volumes and resource limits are resolved in implementation instructions. No real enterprise credentials are required for the first slice.

Required checks include contract schemas, project isolation, token audience/scope rejection, expiry/revocation, injected runbooks, budgets, timeouts, cancellation, crash recovery, duplicate writes, exact-target approvals, stale patches, missing verification signals and manual business gates. Use mock models for deterministic transitions and actual models for quality evaluation. Production SLOs remain proposals from requirements, not measured results.

## 13 Implementation handoff and remaining decisions

Create implementation instructions after reviewing this design. First tasks: resolve compatible Java/Boot/AI/Akka versions and licenses; initialize the build; implement contracts and synthetic fixtures; add identity/project checks; implement two-agent read-only investigation; then prove citations, isolation and bounded execution. Later phases follow roadmap gates.

Open decisions: latest-Java compatibility target; exact Akka version and production license; model weights/hardware; external source OBO feasibility; unattended ingestion identity; actor persistence plugin; business roles; polling windows; disruption policies; sandbox isolation; performance/quality targets; production hosting and residency. Phase 0 cannot be marked Complete until its mandatory decisions and contracts are agreed.

## 14 Primary references

Checked 7 October 2026; mutable documentation must be rechecked when pinning versions.

- [Spring AI reference](https://docs.spring.io/spring-ai/reference/): stable 2.0.1 and provider/tool/vector abstractions.
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html): supported runtime and build-tool ranges.
- [Keycloak token exchange](https://www.keycloak.org/securing-apps/token-exchange): token exchange modes and constraints.
- [Microsoft OBO](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-on-behalf-of-flow): delegated downstream flow.
- [Akka BSL FAQ](https://akka.io/bsl-license-faq): development and production licensing distinction.
- [pgvector](https://github.com/pgvector/pgvector): vector storage and indexing.
- [Kubernetes API initiated eviction](https://kubernetes.io/docs/concepts/scheduling-eviction/api-eviction/): disruption-aware eviction behavior.
