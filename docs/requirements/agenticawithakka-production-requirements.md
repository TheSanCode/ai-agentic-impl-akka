# agenticawithakka Agent Platform Production Requirements

Version 0.1 | 7 October 2026 | Status: Requirements draft for review

## 1 Purpose and decision boundary

agenticawithakka shall help support teams investigate incidents, retrieve authorized knowledge, correlate operational evidence and propose or execute explicitly permitted actions. The platform shall support agents that use reusable skills and controlled tools, with traceable execution and delegated source access.

This document specifies required behavior and acceptance conditions. It does not select an implementation stack. Java, Spring Boot, Akka, model providers, search engines, vector stores, identity providers and deployment platforms remain replaceable candidates. The subsequent technical document shall map these requirements to architecture decisions. Implementation instructions shall derive from that design and retain requirement IDs.

“Shall” denotes a mandatory requirement in the agreed production scope. “Should” denotes a preferred capability. Numerical targets below are proposals until agreed; this draft does not certify production readiness.

## 2 Scope and operating environments

The initial use case is L1.5 support investigation: retrieve runbooks, inspect logs and monitoring evidence, explain likely causes, and draft an incident update. The local lab shall exercise the production contracts using synthetic data and mock platform sources. A production deployment shall connect approved enterprise sources and enforce their access restrictions.

Candidate source categories are knowledge repositories, incident systems, logs, monitoring, work tracking, code repositories, deployment platforms and messaging. Examples include SharePoint, Confluence, ServiceNow, ADX, New Relic, Jira, GitHub, Azure DevOps and Kubernetes. These are candidates, not a commitment to implement every integration in the first release.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| SCP-01 | Support isolated local, test and production environments with separate identities, secrets and data. | A local test cannot reach production sources using its default configuration. |
| SCP-02 | Provide reproducible setup, seeded synthetic documents and mock APIs for a complete local investigation. | A clean environment completes the documented scenario without enterprise credentials. |
| SCP-03 | Separate read, draft and write capabilities. Enable external writes only through explicit policy. | A read-only installation cannot perform a write through any agent or tool. |
| SCP-04 | Preserve the same behavioral contracts between local and production implementations. | Contract tests pass against mock and real adapters; provider-specific differences are documented. |

Autonomous infrastructure remediation, unrestricted terminal execution, model training and scheduled unattended source operations are outside the initial release unless separately approved as scope.

## 3 Users and access boundaries

Proposed roles are platform administrator, project administrator, support member, approver and auditor. A person may hold multiple roles. Final role names and permissions require agreement.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| IAM-01 | Authenticate human users through standards-based federation and support MFA enforcement. | Protected APIs reject missing, expired and invalid credentials; configured MFA is exercised. |
| IAM-02 | Authorize requests using user identity, project membership, operation, source and resource context. Default to deny. | The approved positive and negative permission matrix passes. |
| IAM-03 | Isolate project data, conversations, execution state, indexes and connector configuration. | Two projects cannot obtain each other's protected content through search, tools, memory or exports. |
| IAM-04 | Treat application roles and source permissions as separate constraints. Application admin status shall not grant source access automatically. | An administrator without source permission receives no protected source content. |
| IAM-05 | Enforce access checks server-side at every execution boundary and after relevant identity or policy changes. | Manipulating client requests or agent-generated parameters cannot bypass checks. |
| IAM-06 | Audit access grants, changes and revocations. | An auditor can identify actor, change, time and affected scope. |

## 4 Delegated platform source access

For interactive platform source connections, agenticawithakka shall operate on behalf of the requesting user. OBO means preserving the user's authorized source access, not forwarding one token indiscriminately to every platform. The exact exchange or delegated authorization mechanism is source-specific.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| DEL-01 | Maintain a connector capability matrix recording delegated authorization support, issuer, audience, consent, scopes, refresh behavior and limitations. | Every enabled connector has a reviewed entry and tested authentication path. |
| DEL-02 | Use downstream credentials issued or accepted for the intended source and restricted to required delegated permissions. | Wrong audience and missing scope tests are rejected. |
| DEL-03 | Never silently replace delegated user access with an application credential or shared account. | Unsupported OBO returns a clear unsupported or consent-required result. |
| DEL-04 | Support account linking, consent, expiration, revocation, reauthentication and applicable MFA challenges. | Expired or revoked access stops source operations and produces a recoverable user-facing state. |
| DEL-05 | Keep access tokens, refresh tokens and secrets outside model inputs, outputs, searchable knowledge and general application logs. Encrypt sensitive credential storage. | Inspection and secret-scanning tests find no credentials in those surfaces. |
| DEL-06 | Prevent an agent from choosing or overriding its effective user identity. Bind identity context to authenticated execution. | Forged identity arguments fail authorization. |
| DEL-07 | Revalidate delegated access before a queued, resumed or approved operation. | A revoked user cannot continue a previously paused source action. |
| DEL-08 | Classify unattended ingestion separately from interactive OBO. Any workload identity requires an explicit policy, approved scope and independent audit trail. | No unattended job inherits an unrestricted interactive credential or implicit service-account fallback. |

A local mock shall demonstrate delegated identity propagation. It cannot establish that a real vendor supports OBO; production acceptance requires testing against that vendor's authorization service.

## 5 Agents and execution lifecycle

An agent is a configured execution capability with a purpose, instructions, allowed skills and tools, model access, state and policy boundaries. An agent is not defined by a particular actor framework or transport.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| AGT-01 | Support a coordinator delegating scoped tasks to specialist agents. | An investigation combines knowledge and log evidence with attribution to each specialist. |
| AGT-02 | Implement a bounded loop that selects permitted operations, evaluates results and finishes, pauses or escalates. | Step, time, token and cost budgets terminate runaway execution. |
| AGT-03 | Represent execution states including queued, running, awaiting approval, awaiting authentication, succeeded, failed and cancelled. | State transitions are observable and invalid transitions are rejected. |
| AGT-04 | Persist sufficient execution state for recovery without replaying completed side effects. | Restart during investigation resumes safely; a completed write is not repeated. |
| AGT-05 | Support cancellation and deadlines throughout delegated work. | No new source action starts after cancellation; in-flight outcomes are reconciled and recorded. |
| AGT-06 | Validate structured agent outputs and tool arguments against versioned schemas. | Malformed or out-of-policy output is rejected before execution. |
| AGT-07 | Distinguish evidence, hypotheses, recommendations and completed actions in user results. | A suggested fix is never presented as executed; unsupported conclusions are marked uncertain. |
| AGT-08 | Allow controlled model and agent configuration changes with provenance and rollback. | Each run identifies the versions used and remains inspectable after an upgrade. |

## 6 Communication between agents

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| MSG-01 | Exchange versioned structured task, response, failure and cancellation messages. | Compatible versions communicate; incompatible versions fail explicitly. |
| MSG-02 | Include request ID, execution ID, correlation ID, project, trusted identity reference, deadline and response routing information as applicable. | A delegated task is traceable from user request to final result. |
| MSG-03 | Handle duplicates, delays, missing replies, out-of-order messages and unavailable agents. | Fault-injection tests produce bounded retries or explicit partial failure. |
| MSG-04 | Require idempotency or reconciliation for operations with side effects. Do not assume exactly-once delivery. | Duplicate delivery does not duplicate an incident update. |
| MSG-05 | Apply bounded queues and backpressure, preserving project fairness. | Overload produces controlled admission or rejection rather than unbounded memory growth. |
| MSG-06 | Support logical communication independent of process placement. | Co-located and distributed implementations satisfy the same task contract. |

## 7 Skills and tools

A skill is a reusable versioned procedure combining instructions, applicability, input/output contracts, permitted tools and policy requirements. A tool performs a specific executable operation. Neither is automatically trusted because a model selected it.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| SKL-01 | Register skills with owner, purpose, version, contracts, dependencies and permitted operations. | An agent can discover only skills available to its project and role. |
| SKL-02 | Validate and approve skill changes before production activation; support rollback. | A skill update has a review record and can revert to the previous version. |
| SKL-03 | Load relevant skill content selectively and constrain nested delegation. | Unrelated skills are excluded and recursive skill invocation reaches a configured limit. |
| TOL-01 | Register tools with schemas, risk classification, authorization requirements, timeout and retry behavior. | Every enabled tool has validated metadata and contract tests. |
| TOL-02 | Authorize each invocation using trusted execution context and validated resource parameters. | Model-generated arguments cannot escape permitted resources. |
| TOL-03 | Return structured results and distinguish denied, unavailable, not found and failed outcomes. | Agents handle each condition without inventing successful results. |
| TOL-04 | Support replaceable integration protocols, including direct APIs and standardized tool interfaces where appropriate. | A tool implementation can be exchanged without changing its business contract. |
| TOL-05 | Bound tool output size and protect execution against malicious retrieved content, unsafe paths and unapproved destinations. | Adversarial inputs cannot invoke unauthorized tools or leak data externally. |

## 8 Knowledge ingestion and retrieval

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| RAG-01 | Ingest approved content with source identity, version, timestamps, classification and access metadata. | Every indexed chunk is traceable to an authoritative source record. |
| RAG-02 | Support extraction, chunking, embedding, retries, quarantine and visible ingestion status. | Failed documents are identifiable and can be reprocessed without duplication. |
| RAG-03 | Support keyword, semantic and hybrid retrieval behind replaceable interfaces. | The same evaluation set can compare retrieval strategies. |
| RAG-04 | Store embeddings with model/version and dimension information; support controlled re-embedding and index migration. | A model change cannot silently mix incompatible vectors. |
| RAG-05 | Enforce current source and project permissions before retrieved content enters model context, citations or user-visible results. | Restricted documents never appear for unauthorized users, including through cached results. |
| RAG-06 | Synchronize source updates, deletions and permission changes within an agreed freshness window. Fail closed if permission currency cannot be established under the agreed policy. | Revocation and deletion tests prevent access within the approved window. |
| RAG-07 | Provide source citations and indicate unavailable, stale or insufficient evidence. | A response can be traced to retrieved passages; absent evidence is not fabricated. |
| RAG-08 | Treat source repositories as authoritative. Any intermediate content copy requires a defined purpose, retention policy and access controls. | The ingestion design documents why each copy exists and how it is deleted. |
| RAG-09 | Retain source links and access metadata through extraction and chunking. | Permission enforcement and provenance survive document transformation. |

An embedding model creates vectors; a vector store persists and searches them. Separate databases for “embeddings” and “vectors” are not a requirement. Search and vector storage may be combined or separate if the behavioral requirements are met.

## 9 Approval and action governance

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| GOV-01 | Classify operations by impact and require explicit human approval for policy-defined actions. | A protected write cannot execute without a valid approval. |
| GOV-02 | Present the exact target, action, parameters, expected effect and relevant evidence to the approver. | The review identifies precisely what will change. |
| GOV-03 | Bind approval to user/project, immutable action content, approver, expiry and execution ID. | Changed parameters, expired approval or wrong project invalidate approval. |
| GOV-04 | Recheck authorization and relevant resource conditions immediately before execution. | Permission revocation or conflicting source changes stop execution. |
| GOV-05 | Record action outcome, source receipt and any compensation or reconciliation requirement. | An uncertain timeout is reconciled before a potentially duplicating retry. |

## 10 Data protection and audit

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| DAT-01 | Encrypt protected data in transit and at rest; support credential rotation and access minimization. | Configuration review and rotation tests pass. |
| DAT-02 | Apply explicit policies for model-provider data use, residency, retention and permitted classifications. | Restricted content cannot be sent to an unapproved provider. |
| DAT-03 | Define retention and deletion for conversations, execution state, source copies, vectors, caches and backups. | A deletion request is traceable across affected stores within the agreed policy. |
| AUD-01 | Audit user requests, delegation, tools, policy decisions, approvals and external actions with timestamps and correlation IDs. | An auditor reconstructs an investigation without accessing credentials. |
| AUD-02 | Protect audit records from unauthorized alteration and restrict sensitive payload access. | Unauthorized audit edits fail and retention controls are tested. |
| AUD-03 | Record evidence and decision summaries without requiring storage of private model reasoning. | Operational review works from inputs, approved summaries, tool results and outcomes. |

## 11 Reliability and operating targets

Production shall tolerate dependency failures, enforce capacity limits and expose user-understandable partial results. A failed specialist must not cause the coordinator to fabricate its findings.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| OPS-01 | Provide health checks, dependency status, graceful shutdown and controlled recovery. | Dependency loss and process restart tests pass. |
| OPS-02 | Support bounded retries, circuit breaking and source/model rate limits. | Throttling does not create retry storms. |
| OPS-03 | Monitor execution duration, tool errors, queue depth, retrieval quality, token/cost use and authorization denials. | Operators can diagnose a failed run using correlated telemetry. |
| OPS-04 | Back up durable state and configuration and test restoration. | A restore drill meets the agreed recovery objectives. |
| OPS-05 | Support versioned deployment, configuration validation, migration and rollback procedures. | A release rehearsal demonstrates safe upgrade and rollback. |

Proposed targets for sizing discussion, not commitments:

| Measure | Proposed baseline | Qualification |
| --- | --- | --- |
| Monthly availability | 99.9% | User-facing platform; dependency treatment and maintenance exclusions to agree. |
| Request acknowledgement | p95 at most 2 seconds | Excludes model completion; measure at agreed load. |
| Interactive investigation | p95 at most 60 seconds | Defined read-only scenario, healthy dependencies and bounded evidence volume. |
| Initial concurrency | 25 active investigations | Project mix, hardware and request profile to define. |
| Recovery point objective | At most 15 minutes | Durable business state; external completed actions require reconciliation. |
| Recovery time objective | At most 60 minutes | Agreed failure scenario and recovery environment. |
| Content freshness | At most 15 minutes where supported | Connector-dependent; permission enforcement follows the approved revocation policy. |

## 12 Replaceability and open source policy

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| ARC-01 | Define stable contracts for models, execution runtime, identity, authorization, search, vector storage and connectors. | A replacement does not require rewriting core investigation rules. |
| ARC-02 | Keep provider-specific behavior in adapters and document capability differences. | Unsupported capabilities fail explicitly instead of producing silent behavioral changes. |
| ARC-03 | Use open-source authentication and authorization components. Search and vector-storage components shall be open source. | Selected licenses and required features satisfy the agreed open-source definition. |
| ARC-04 | Record licenses for runtime dependencies, models and embedding weights. Identify source-available components separately. | License inventory explicitly identifies any exception requiring agreement. |
| ARC-05 | Export application records, skill definitions and source metadata in documented formats; provide rebuild or migration procedures for indexes. | A migration exercise preserves business records and reconstructs usable retrieval. |

Replaceability does not imply zero migration work. Identity changes can require reauthentication, model changes can require re-embedding, and runtime changes can require state migration. These costs must be explicit in the technical design.

## 13 Production acceptance scenarios

| Scenario | Required result | Principal requirements |
| --- | --- | --- |
| Successful investigation | Authorized runbooks and logs produce a cited explanation and draft update. | AGT-01, RAG-07, TOL-03 |
| Different source permissions | Two users asking the same question receive only their permitted evidence. | IAM-04, DEL-02, RAG-05 |
| Forged identity or cross-project request | Access is denied without revealing protected content. | IAM-03, DEL-06 |
| Unsupported delegated source | Connector reports unsupported access without a shared-account fallback. | DEL-01, DEL-03 |
| Revocation during execution | Pending source operations stop; stale evidence follows the agreed access policy. | DEL-07, RAG-06 |
| Prompt injection in a document | Content cannot override identity, policies, tool permissions or approved destinations. | TOL-02, TOL-05 |
| Agent or model failure | Bounded recovery or explicit partial failure with no invented evidence. | AGT-02, MSG-03, OPS-02 |
| Duplicate write request | Exactly one intended business effect, or explicit reconciliation of uncertain outcome. | MSG-04, GOV-05 |
| Approval changed or expired | Action is blocked and requires a fresh approval. | GOV-03, GOV-04 |
| Restart while awaiting approval | Pending state survives and permissions are rechecked before continuing. | AGT-04, DEL-07 |
| Source deletion or access change | Index and caches no longer expose unauthorized material under agreed timing. | RAG-05, RAG-06 |
| Component replacement | Contract and evaluation suites pass with documented differences. | ARC-01, ARC-02 |
| Backup restoration | State is restored and uncertain external actions reconciled. | OPS-04, GOV-05 |

Security and authorization scenarios are release blockers. Quality evaluation shall use a versioned benchmark with human-reviewed expected evidence, tool choices and outcomes. Retrieval relevance, grounded answers, task success, latency and cost thresholds require agreement before production acceptance.

## 14 Delivery sequence

1. Requirements baseline: agree scope, delegated identity policy, role matrix, acceptance scenarios and operating targets.
2. Technical design: choose components; define contracts, data models, permission enforcement, deployment, recovery and migrations. Trace each decision to requirement IDs.
3. Implementation instructions: convert the design into ordered tasks, repository conventions, configuration, tests and completion criteria.
4. Local proof: one coordinator, two specialists, synthetic evidence, mock OBO, approval and restart demonstrations.
5. Integration proof: one real source with verified delegated authentication, permissions and revocation handling.
6. Production hardening: complete threat review, evaluation, capacity tests, recovery drills and operational documentation.

Passing the local proof demonstrates behavior in a lab. Production readiness requires the production acceptance evidence and agreed operational gates.

## 15 Decisions to resolve before technical design

| Decision | Proposed starting position |
| --- | --- |
| First production workflow | Read-only incident investigation with approval-gated incident updates. |
| First real sources | One knowledge repository and one operational source. Exact platforms to choose. |
| OBO policy | Mandatory for interactive source calls; unsupported sources remain disabled. |
| Ingestion identity | Separately approve workload ingestion or restrict to user-triggered delegated ingestion. |
| Project membership | Support multiple project memberships; confirm whether existing one-project-per-user policy must remain. |
| Approver separation | Define actions requiring a different approver from the requester. |
| Open-source exceptions | Decide whether source-available runtime or model components are acceptable. |
| Hosting and residency | Determine enterprise environment, region and allowed data classifications. |
| Model execution | Local, private enterprise or approved hosted provider; choose independently of agent runtime. |
| Permission freshness | Agree source-specific revocation limits and fail-closed behavior. |
| Capacity and quality | Confirm load profile, SLOs, evaluation thresholds and budget limits. |
| User experience | Confirm browser interface, API-only lab or both; additional channels follow later. |

These decisions remain open. No particular library, framework, protocol implementation or vendor is selected by this requirements draft.
