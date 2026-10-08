# AgenticaWithAkka Phase 1 Implementation Instructions

Version 0.2 | 7 October 2026 | Implementation handoff

## 1 Scope and source authority

Build a read-only local investigation lab with two logical roles: Coordinator and Investigation. Investigation initially combines knowledge retrieval and log analysis. Follow the [production requirements](../requirements/agenticawithakka-production-requirements.md), [technical design](../design/technical-design.md), [agentic design](../design/agentic-design-and-phased-plan.md) and [roadmap](../ROADMAP.md).

Technical design baseline after document review: blob `094265a1818ccf1a8845a343c6f7bb75055d0127` (version 0.2). This document specifies tasks; it does not claim they have been implemented. Read applicable AGENTS.md instructions and current files before work. Do not overwrite unrelated changes.

Keep pod restart execution, Jira patch writes, real enterprise connectors, business review and production deployment out of this slice. Their contracts may be reserved without exposing executable write tools. Do not mark later phases complete because placeholder interfaces exist.

## 2 Bootstrap decisions

Use the technical design's proposed Java 25 baseline, Spring Boot 4 and Spring AI stable candidate. Resolve current compatible patched versions and record exact build/container/model versions in a dependency decision record before adding dependencies. A latest-GA Java profile is a compatibility experiment, not a substitute for tested support.

Prefer one Maven application with wrapper and packages rather than premature service decomposition. Use Akka Typed in the local runtime if Java/dependency/licensing checks pass; otherwise retain a documented in-process adapter behind AgentRuntime and leave the Akka-specific work open. Do not hide a runtime substitution. A non-Akka bootstrap is not an Akka demonstration. The intended lab completion evidence shall include typed request/reply, asynchronous results, deadlines and cancellation on the selected Akka adapter; otherwise record that objective as outstanding. Durable actors remain Phase 2.

Local components: PostgreSQL/pgvector, Keycloak, Ollama and seeded mock sources. Pin dependency and image versions. Keep model weights/digests and hardware requirements explicit. An installation blocked by unavailable dependencies should produce a clear blocker rather than invented test success.

## 3 Ordered tasks and evidence

| Task | Work | Completion evidence |
| --- | --- | --- |
| P1-01 | Inspect repository, confirm build/runtime decisions and record license inventory. | Decision record lists resolved versions, tested compatibility and remaining exceptions. |
| P1-02 | Initialize application, wrapper, package boundaries, configuration validation and health endpoint. | Clean checkout builds and starts using documented prerequisites. |
| P1-03 | Add local infrastructure profiles and synthetic fixtures. | Repeatable setup seeds both projects and mock sources without enterprise credentials. |
| P1-04 | Implement trusted identity, project policy and mock delegated connector access. | Missing credentials, wrong audience/scope and unauthorized project access fail. |
| P1-05 | Define versioned agent/task/tool/evidence contracts. | Schema and argument validation tests reject malformed inputs. |
| P1-06 | Implement skill/tool registry and permission checks. | Only permitted read tools are discoverable and executable. |
| P1-07 | Implement ingestion and hybrid search with evidence citations. | Expected documents are found; restricted chunks never enter model context. |
| P1-08 | Implement Coordinator and Investigation with bounded asynchronous execution. | Delegation produces a structured result; deadlines, cancellation and budgets stop work. |
| P1-09 | Expose create/read/cancel APIs and redacted execution tracing. | API tests and trace inspection establish correct states and no credential leakage. |
| P1-10 | Run actual-model evaluation and negative end-to-end scenarios. | Versioned results identify successes, failures and missing evidence. |
| P1-11 | Document setup, demo, limitations and update roadmap from evidence. | Another developer can reproduce the lab; unchecked gates remain visible. |

Complete dependent tasks in order. Read-only independent validation may run concurrently. Commit coherent reviewable changes to the authorized branch. Do not merge, deploy or create/assign issues unless separately requested.

## 4 Suggested package boundaries

Use a single application under an agreed package namespace with domain/contracts, application, agents, skills, tools, security, connectors/mock, retrieval, persistence, api and observability packages. Place migrations in resources, synthetic fixtures under test or lab resources, infrastructure under infra/local and evaluation cases under evaluation.

Domain objects must not depend on Spring AI, Akka, vendor tokens or HTTP transport types. Use ModelGateway, AgentRuntime, SearchGateway, SourceConnector, DelegatedTokenProvider and PolicyDecisionService ports. Keep construction and adapter configuration in composition code.

## 5 Synthetic scenario

Create projects Alpha and Beta, with fictional service names, a connection-timeout incident, timestamped mock logs, a relevant runbook, an irrelevant runbook and a malicious document attempting to override tools. Avoid real customer identifiers, secrets and internal enterprise content.

Seed users with explicit access: one Alpha-only member, one Beta-only member, and a project administrator whose application role does not grant extra source permissions. Define a restricted source document visible only to one entitled user. Give documents source IDs, versions, classification, timestamps and permission references.

The demo request is: “Investigate connection timeouts in the Alpha service using the available runbook and logs.” The expected response contains cited evidence, an explained hypothesis, limitations and recommended next steps. It must not claim a fix or restart was performed.

## 6 Identity and mock delegation

Configure OIDC for the local API and enforce project membership server-side. Validate issuer, audience, signature and expiry. Never authorize by an unverified project/user field in request text.

The mock source shall independently validate its intended delegated credential and scopes. Bind token acquisition to authenticated context, not model arguments. Demonstrate source-specific audience rejection and user-level access differences. If actual token exchange cannot be configured, explicitly label the connector test as a simulation and leave the exchange acceptance check incomplete.

Keep secrets out of source control, prompts, task payloads and logs. Provide example configuration containing placeholders. Recheck access at tool invocation and evidence retrieval. Limit the lab to interactive ingestion unless a separate ingestion identity policy is agreed.

## 7 Agent and tool behavior

Coordinator validates the request and delegates a scoped task with execution ID, task ID, project, trusted identity reference and deadline. Investigation retrieves documents and queries mock logs, then returns evidence references and structured findings. Coordinator combines results and returns facts separately from hypotheses.

Use illustrative typed contracts such as StartInvestigation, InvestigateEvidence, EvidenceFound, TaskFailed and CancelInvestigation. If using Akka, adapt specialist replies to coordinator commands and return asynchronous model/API completions into the actor mailbox. Do not block dispatchers or infer durable delivery from tell/ask.

Register only searchKnowledge, queryMockLogs and inspectMockHealth as read operations. Validate arguments, allowed time ranges, source IDs, output size and deadlines. The policy service decides access before the tool adapter runs. Unknown or forbidden tools fail explicitly.

Represent Queued, Running, AwaitingAuthentication, Succeeded, Failed and Cancelled states. Expired delegated access stops new calls and requires reauthentication by the same subject/project before continuing within the original deadline. Approval and business-review states may exist in contracts but are not implemented workflows in this phase. Persist execution summaries if appropriate; do not claim restart-safe continuation until Phase 2 recovery tests pass.

Set configurable step, time, token, concurrency and output limits. Budget values are lab settings documented with the selected model. Cancellation prevents new operations; late results cannot change a terminal cancelled state.

## 8 Retrieval and model integration

Extract/chunk synthetic runbooks and store vectors with embedding model/version and dimension. Use PostgreSQL full-text and pgvector candidates behind SearchGateway, then combine rankings with a documented strategy. Apply project/source filtering and a current authorization check before including any text in prompts.

Return source IDs, links, versions and passages. Do not expose restricted document titles or snippets through citations, summaries or caches. Treat retrieved content as evidence, never as authority to change system instructions.

Use a mock model for deterministic workflow tests, then a real local model to assess tool choice and grounded answers. Pin the actual model digest and record prompt/skill versions. Unsupported structured/tool output must produce a validation failure rather than fabricated evidence. Agent failure should be visible and bounded.

## 9 API contract

Propose POST /api/projects/{projectId}/investigations to create an execution, GET on its execution resource for results and POST on its cancel subresource. Return a server-generated execution ID; use consistent Denied, InvalidInput, Unsupported, Conflict, RateLimited and DependencyUnavailable errors.

All read/cancel operations recheck identity and ownership/project permissions. Recheck current source access before returning stored evidence, summaries or citations; withhold protected content if its permission currency cannot be established. Pagination and bounded payloads are required. Do not expose token contents or stack traces. A browser interface can follow after the API flow works; no dashboard or seven-agent deployment is required to pass this slice.

## 10 Acceptance suite

| Check | Expected outcome | Requirement mapping |
| --- | --- | --- |
| Authorized demo | Cited runbook/log evidence, hypotheses and no claimed action. | AGT-01, AGT-07, RAG-07 |
| Other project or restricted document | Denied or filtered before model exposure; no protected snippets. | IAM-03, RAG-05 |
| Application admin without source access | Source restrictions still apply. | IAM-04 |
| Invalid/expired/wrong-audience token | Request or downstream operation rejected. | IAM-01, DEL-02 |
| Expiration during a task | AwaitingAuthentication; no shared-identity fallback; resume only with valid same-user access and deadline. | DEL-04, DEL-07 |\n| Revocation after completion | Stored results and citations no longer expose revoked content. | IAM-05, RAG-05 |\n| Forged user field | Cannot change effective identity. | DEL-06 |
| Prompt-injected runbook | Cannot add tools, override policy or leak data. | TOL-05 |
| Missing runbook/log source | Explicit gap or partial failure; no invented evidence. | TOL-03, AGT-07 |
| Unknown tool or invalid arguments | Rejected before execution. | AGT-06, TOL-01 |
| Loop budget, timeout and cancellation | Bounded termination and consistent terminal state. | AGT-02, AGT-05 |
| Output/log inspection | No access tokens, secrets or private model reasoning. | DEL-05, AUD-03 |

Record commands and actual test outputs. Do not report unrun tests as passed. Model evaluation thresholds are agreed before declaring quality acceptance. Verify schema/migrations and negative access cases before broadening tests.

## 11 Lab policy decisions before acceptance

Record the provisional role/tool matrix, model and image versions, source scopes, MFA enforcement test, lab reset/retention policy, concurrency limits and evaluation thresholds. Demonstrate that default configuration cannot call production sources. Keep unresolved lab acceptance decisions visible; do not substitute production approval with lab defaults.

## 12 Handoff and roadmap updates

Provide a local setup guide with exact prerequisites, start/seed/demo/stop procedures, ports, resource needs, model installation, expected output and known limitations. Verify commands against the implemented files; do not publish speculative runnable commands.

Use project-roadmap to check off only evidenced tasks. Phase 0 remains open while required decisions/contracts are unresolved. Phase 1 remains Planned until implementation begins, and Complete only after mandatory exit criteria pass. This instructions document alone completes neither phase.

The next action is P1-01 followed by repository scaffolding. Keep production OBO, actor recovery, operational writes and business review pending until their later implementation and acceptance evidence exist.
