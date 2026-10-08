# AgenticaWithAkka Technical Design

Version 0.7 | 7 October 2026 | Proposed implementation design

## 1 Scope and source baseline

Implement a local-first modular application with replaceable adapters, initially using Coordinator and Investigation agents. Expand to the six roles defined in the [agentic design](agentic-design-and-phased-plan.md). Follow [requirements v0.6](../requirements/agenticawithakka-production-requirements.md) and the [roadmap](../ROADMAP.md). This is a proposed design, not evidence that Phase 0 is complete.

Source baseline on feature/agentic: requirements blob `58fb239de256814832cb65481c9ab5f01f60c797`, agentic design blob `a4ba17ad415cb9583d0d01267d09cd6463e0f83d`. Requirements remain authoritative if this design conflicts with them.

## 2 Proposed technology baseline

See the [technology stack](technology-stack.md) for the complete web, messaging, background execution, testing and operations selections. These remain compatibility-tested candidates, with exact dependencies pending bootstrap validation.

| Concern | Proposal | Replacement boundary and unresolved checks |
| --- | --- | --- |
| Browser UI | Angular 22, TypeScript and Angular Material | Frontend/API contract; resolve matching Node/TypeScript versions. |
| Messaging | Telegram Bot API; Twilio WhatsApp/SMS candidates | ChannelGateway, secure account linking and provider-specific webhook validation. |
| Java | Java 25 LTS baseline plus a latest-GA Java compatibility profile | Do not call Java 25 the latest release. Resolve the latest GA toolchain and test Boot, AI and actor dependencies before enabling that profile. |
| API and composition | Spring Boot 4; initial compatibility candidate 4.0.8 | Use current supported patched release at implementation; verify with Spring AI BOM rather than mix untested versions. |
| Model integration | Spring AI 2.0.1 stable candidate | ModelGateway isolates framework/provider types. Milestone versions are optional experiments. |
| Agent runtime | Akka Typed, with durable orchestration introduced in Phase 2 | Resolve exact version, Java support, persistence plugin and licensing. Provide AgentRuntime interface for an alternative. |
| Local inference | Ollama with selected tool-capable chat and embedding models | Verify hardware fit and model-weight licenses. Pin model digest and embedding dimension. |
| Identity | Keycloak plus Spring Security | IdentityContext and DelegatedTokenProvider contracts. Validate specific token-exchange configuration. |
| Policy | Server-side authorization service; OPA adapter later if useful | Deny-first PolicyDecision contract; no model makes binding access decisions. |
| Business state | PostgreSQL with schema migrations | Repository interfaces; separate identity-provider database from application schema. |
| Retrieval | PostgreSQL full-text search plus pgvector initially | SearchGateway supports hybrid retrieval; optional OpenSearch profile for comparison. One vector store is sufficient. |
| Local operation | Podman Compose as the open-source default, with an opt-in kind cluster in Phase 4 | Verify the Compose provider and kind compatibility. Pin images. Select resource budgets after measuring laptop capacity. Docker Compose remains a fallback if compatibility testing requires it. |
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

## 14 Review clarifications and implementation gates

### Execution result and change result

TaskResult status uses Completed, Failed or Cancelled, plus an explicit coverage field Complete or Partial. Completed means the requested task finished; it does not assert that a hypothesis is correct, a write succeeded or a business change is verified. Execution Succeeded is reserved for meeting that execution's declared task criteria.

A change record has its own status: Proposed, AwaitingApproval, Approved, Executing, AwaitingTechnicalVerification, AwaitingBusinessValidation, Verified, Failed or Cancelled. Verification outcomes retain the technical and business states defined above. Failed or inconclusive technical verification cannot reach Verified. Deployment and restart observations are evidence events, not change acceptance.

### Expired access and protected historical evidence

Phase 1 shall support AwaitingAuthentication as well as queued/running/terminal states. If delegated access expires, stop new source calls and show an authentication-required result. Resume only after the same authenticated subject/project establishes valid access and the task deadline remains valid. Never convert expiration into a successful empty result.

Reauthorize stored evidence at result rendering, admin viewing and export, not only at initial retrieval. Evidence visibility may change after a run completes. Filter or withhold content when current source authorization cannot be established under the agreed policy. Do not expose protected content through historic summaries, citations, caches or inferred titles. Preserve restricted audit evidence under its own access policy.

### Phase 1 and runtime evidence

A non-Akka adapter may support a clearly labeled bootstrap experiment while compatibility is resolved; it cannot satisfy the Akka communication learning objective. Before claiming the intended Akka lab complete, demonstrate typed request/reply, asynchronous completion, deadline handling and cancellation using the selected Akka adapter. Durable recovery remains Phase 2.

Define provisional lab policy/configuration values in decision records: role-to-operation matrix, source audience/scopes, MFA test case, retention/reset behavior, queue/concurrency limits, retrieval ranking configuration and model-quality acceptance criteria. Provisional lab choices do not approve production SLOs, source delegation or licenses.

Before production implementation, complete the role/source matrix, API schemas and transition table, source revocation strategy, persistence/plugin decision, deletion/audit retention policy, verified dependency inventory, restart approval separation and technical/business verification thresholds. This document review is not acceptance of those unresolved decisions.

## 15 Primary references

Checked 7 October 2026; mutable documentation must be rechecked when pinning versions.

- [Spring AI reference](https://docs.spring.io/spring-ai/reference/): stable 2.0.1 and provider/tool/vector abstractions.
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html): supported runtime and build-tool ranges.
- [Keycloak token exchange](https://www.keycloak.org/securing-apps/token-exchange): token exchange modes and constraints.
- [Microsoft OBO](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-on-behalf-of-flow): delegated downstream flow.
- [Akka BSL FAQ](https://akka.io/bsl-license-faq): development and production licensing distinction.
- [pgvector](https://github.com/pgvector/pgvector): vector storage and indexing.
- [Kubernetes API initiated eviction](https://kubernetes.io/docs/concepts/scheduling-eviction/api-eviction/): disruption-aware eviction behavior.

## 16 Durable background execution design

Return HTTP 202 for accepted asynchronous work with executionId and a status URL. Disconnecting the caller does not cancel execution. Capture trusted identity/project references; do not persist browser cookies or raw bearer tokens as task identity. Logout policy remains explicit and revocation always prevents unauthorized source calls.

Phase 1 can retain in-process execution with recorded terminal failure for work interrupted by application restart. Phase 2 must commit durable acceptance before acknowledging durable work and use a recoverable dispatch mechanism. Persist loop position, cumulative budget, completed operation references, wait reason, absolute deadline, nextWakeAt and execution version. Revalidate access and source credentials on wake-up.

Use a durable scheduler for nextWakeAt, with ownership leases and fencing or equivalent exclusive execution. An expired lease does not prove an external write stopped: the action executor must still enforce idempotency/reconcile receipts and reject stale ownership where enforceable. Persist timer intentions; transient actor timers only deliver wake-up signals.

Active-time budgets exclude human waits; the absolute deadline includes them unless an authorized extension is recorded. Steps/tokens/cost/retry usage never reset on recovery. Define cancellation as preventing new actions; reconcile in-flight outcomes and do not imply automatic rollback. Wait expiration produces a documented terminal or escalation outcome.

Polling runs as scheduled bounded observations, not an always-running AI loop. Model interpretation is invoked only when needed. Use per-project admission, bounded queues, backoff and terminal/manual-review handling. Progress API and admin view show last update/checkpoint, active step, usage, pending wait and next wake-up without protected content.

Phase 2 fault tests include crash around durable admission/checkpoints, restart while waiting, repeated wake-ups, two competing workers, cumulative budget exhaustion, revocation and cancellation. Do not call actor persistence alone an end-to-end exactly-once guarantee.

## 17 WhatsApp Telegram and SMS adapters

Use a shared ChannelGateway and separate provider adapters. Normalize a validated inbound event into channel, providerEventId, conversationId, verifiedSenderRef and request text; resolve linked application identity server-side. The channel adapter calls the existing investigation application service, not a parallel agent workflow.

Expose provider-specific authenticated webhook endpoints with bounded payloads, deduplication and rate limits. Acknowledge provider receipt promptly, then dispatch normalized work through the background execution path. Phase 2 durable admission is a prerequisite for reliable provider retry handling.

Enroll users through application login and a short-lived channel ownership challenge. Record linked subject/project entitlements separately from transport identifiers; phone ownership is not enterprise identity. Reject unlinked or revoked mappings and recheck access at each request. Source token acquisition continues through source-specific delegated flows; never substitute channel credentials for source credentials.

Send redacted execution summaries and expiring authenticated links by default. Require login to view protected details. Disallow group initiation until an explicit conversation policy is approved. Use a delivery outbox, retry/backoff and provider receipts where available; keep delivery status independent of workflow status. Do not assume a delivery receipt proves a user reviewed or approved anything.

Provider APIs, approval steps, session/message rules, SMS registration, pricing and outbound consent constraints require current official-provider verification during adapter selection. No provider or chat-native approval mechanism is selected here. Local development uses mock channel events first, followed by real sandbox accounts; no real messages are sent as part of this documentation update.

## 18 Browser application architecture

A browser interface is required, not an API-only optional deliverable. Select the frontend framework through a decision record; Angular 22 with Angular Material is the recommended candidate recorded in the technology-stack document; exact compatible frontend dependencies remain to verify. Use a replaceable frontend consuming the same project-scoped application APIs as channel adapters.

Phase 1 screens: sign-in, authorized project selection, new investigation, execution list, progress/results and cancellation. Use bounded authenticated status polling first; SSE may be added through a capability adapter. Browser navigation or closure does not cancel server work. Prevent duplicate submission using request identifiers and disable/handle repeated submission safely.

Later screens: exact-action approval in Phase 4, technical verification/business review and complete admin overview in Phase 5, channel account linking and secure messaging deep links in Phase 6. Local fake UI controls do not count as implemented protected workflows.

Prefer same-origin frontend/API with the backend OIDC session design described above. If separate origins are chosen, explicitly design CORS, CSRF, cookies, token handling and redirects. Display source/model content as text or sanitized markup, never trusted executable HTML. Reauthorize every server request and render safe loading/denied/expired/partial states. Choose and test a browser support matrix and accessibility target before acceptance.

## 19 AKS workload and access topology

AKS is the target runtime; local Compose and kind remain development profiles. Deploy separate UI and API Deployments with ClusterIP Services. A Deployment manages one or more replica pods, so “separate pods” means independent workload boundaries rather than exactly one pod per component.

| Workload | Container/base image | Exposure |
| --- | --- | --- |
| UI | Build Angular with approved Node builder; serve compiled static assets with approved non-root web-server base image | Application gateway routes / to UI Service. Node is not required in the final static-serving image. |
| API | Build with approved JDK/Maven builder; run on approved compatible Java runtime base | Same application origin routes /api and login callbacks to API Service. |
| Agent workers | Java runtime base, potentially the same application image in worker mode | Internal only; separate Deployment from Phase 2 durable processing onward. |
| Channel adapters | Initially API modules, later optional dedicated gateway Deployment | Only validated webhook paths exposed through controlled external ingress. |
| Code-validation jobs | Approved disposable build/test images | Isolated Jobs with no public routes, production credentials or host socket. |
| Inference | Approved model-serving image and versioned models, or approved external/private endpoint | Internal endpoint; GPU node pool only if measured requirements justify it. |
| Identity/data | Supported hardened service images or separately approved hosted equivalents | Controlled identity endpoints; databases/internal stores stay private with durable storage and backup. |

Separate UI/API deployment is recommended for independent releases, scaling and smaller privileges. A combined container is possible for a throwaway demo but is not the recommended AKS layout. Do not create one pod per agent or one pod per messaging channel merely because it is a logical role.

Browser downloads Angular assets, then calls the API through the same-origin gateway. Browser users never call agent pods or databases directly. Provider webhooks enter narrowly exposed API/channel endpoints and invoke the same workflow service. Private browser access and external-provider callback reachability are separate decisions; a private-only application needs a permitted external relay/gateway or alternative provider delivery model.

Use TLS, approved gateway implementation, route/auth policies, size/rate limits and private internal Services. Specific ingress product is unresolved; verify AKS support/lifecycle before selection. Keycloak callbacks and public identity endpoints need explicit routing. Shared backend sessions or a documented stateless session strategy are required before scaling API replicas; do not rely on per-pod browser sessions.

Approve base images separately for Node build, Java build/runtime, web serving and validation jobs. Use multi-stage builds, no secrets in layers, non-root execution, read-only filesystems where feasible and narrow capabilities. Store images in approved registry/ACR with immutable release digests, SBOM, scans and rebuild pipelines. ACR is the recommended Azure registry candidate, not provisioned infrastructure.

Workload Identity supports infrastructure access such as approved secrets retrieval; kubelet registry pulls use their configured identity. Neither is a user-delegated source token. Use scoped credentials and policies for user-authorized Kubernetes remediation.

Version Helm charts/manifests with environment values, requests/limits, readiness/liveness/startup probes, shutdown grace, autoscaling and disruption settings. Actor workers need explicit ownership/persistence and drain behavior before multiple replicas; API HPA cannot by itself make actor state safe. Keep durable records outside ephemeral pods.

Local PostgreSQL/Keycloak/pgvector containers do not establish production high availability. Decide stateful hosting, backup/restore, encryption and availability separately; managed replacements need review against the open-source requirements. Model weights and vector storage require deliberate persistence and resource sizing.

References: [AKS baseline](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/containers/aks/baseline-aks), [AKS best practices](https://learn.microsoft.com/en-us/azure/well-architected/service-guides/azure-kubernetes-service), [ACR base-image updates](https://learn.microsoft.com/en-us/azure/container-registry/container-registry-tasks-base-images).
