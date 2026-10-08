# agenticawithakka Agent Platform Production Requirements

Version 0.7 | 7 October 2026 | Status: Requirements draft for review

## Sections added in version 0.3

The following sections are included in this document after Section 15:

- Section 16 Operational remediation and code proposals: REM-01 through REM-05 cover pod restart safety and recovery; COD-01 through COD-05 cover Jira-linked code proposals.
- Section 17 Admin issue overview: ADM-01 through ADM-05 cover the admin eagle view, evidence, timelines and access controls.
- Section 18 Additional production acceptance scenarios: restart, code proposal and admin access tests.
- Section 19 Post change verification and business acceptance: VER-01 through VER-12 cover runbook-based planning, post-deployment log polling, technical checks and mandatory manual business validation for functionality changes.

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

Unapproved autonomous infrastructure remediation, unrestricted terminal execution, model training and scheduled unattended source operations are outside the initial release unless separately approved as scope. Approval-gated workload restarts and Jira-linked code proposals are in scope.

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
| User experience | Browser web application is required; messaging channels complement it. |

These decisions remain open. No particular library, framework, protocol implementation or vendor is selected by this requirements draft.


## 16 Operational remediation and code proposals

The platform shall extend investigation into approval-gated pod restart execution and Jira-linked code-change proposals. These capabilities remain subject to delegated source access, source permissions, project isolation and action governance.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| REM-01 | Inspect workload health and diagnostic evidence before proposing a restart. Identify cluster, namespace, workload owner, target pod and resource identity. | The proposal shows the exact target and supporting evidence. |
| REM-02 | Distinguish deleting a managed pod for replacement from restarting a deployment or another workload. Validate the chosen operation against workload type, available capacity, disruption policy and configured safety limits. | Unsafe or unsupported targets are blocked; a pod action does not silently become a deployment-wide restart. |
| REM-03 | Require explicit approval and revalidate delegated permissions, target identity and safety conditions immediately before execution. | Revoked permissions, a changed target or an expired approval blocks the restart. |
| REM-04 | Bound restart attempts, enforce cooldowns and reconcile uncertain outcomes before retrying. | Repeated requests cannot create a restart loop or duplicate an uncertain operation. |
| REM-05 | Observe replacement or restarted workload readiness and relevant service health within a configured timeout. Record verified recovery, failure or unresolved outcome and escalate where appropriate. | Issuing a restart request alone is not reported as successful recovery. |
| COD-01 | Read an authorized Jira ticket, its acceptance criteria and relevant repository context to propose a code change. | The proposal links the ticket, repository, base branch and exact commit used for analysis. |
| COD-02 | Produce a reviewable patch with rationale, affected files, expected behavior, risks and proposed tests. Distinguish tests executed from tests merely recommended. | Reviewers can inspect the actual diff and validation evidence without relying on a narrative claim. |
| COD-03 | Treat ticket text and repository content as untrusted inputs; protect secrets and execute validation only within a restricted environment. | Malicious ticket or code content cannot trigger arbitrary privileged execution or credential disclosure. |
| COD-04 | Recheck repository state before applying a proposal. Creating a branch, pushing changes, opening a draft PR or updating Jira requires explicit policy authorization and an audit trail. Merging and deployment require separately defined authorization. | A stale patch is rejected or regenerated; the agent cannot infer merge permission from permission to suggest code. |
| COD-05 | Link approved code proposals and optional draft PRs to the originating issue and investigation record. | Users can trace a proposal from Jira to evidence, patch and review status. |

## 17 Admin issue overview

The admin eagle view shall present a consolidated view of an issue across authorized sources. It shall distinguish observed facts, agent hypotheses, proposed actions and verified outcomes.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| ADM-01 | Show issue summary, severity, impact, affected services and dependencies, investigation owner and current status. | An administrator can identify the scope and current state from one issue view. |
| ADM-02 | Correlate a timestamped timeline of logs, metrics, alerts, incidents, Jira tickets, deployments and repository changes with source links. Mark uncertain correlations and distinguish event time from ingestion time. | The view shows provenance and does not present temporal correlation as proven causation. |
| ADM-03 | Show agent tasks, findings, evidence, failures, pending approvals, restart attempts, code proposals and action outcomes. | The issue view reconstructs the investigation and identifies blocked or unresolved work. |
| ADM-04 | Apply project and source authorization to every view, export and drill-down. Admin status alone shall not grant source access or remediation authority. | Protected details remain unavailable to an admin without source permission; restricted source content is not exposed through summaries. |
| ADM-05 | Display data freshness, unavailable sources and partial coverage. Support authorized filtering and drill-down by issue, service and time range. | Missing source evidence is distinguishable from absence of incidents or errors. |

## 18 Additional production acceptance scenarios

| Scenario | Required result | Principal requirements |
| --- | --- | --- |
| Approved managed pod restart | Exact target is restarted, readiness checked and outcome recorded. | REM-01 through REM-05 |
| Unsafe restart or changed target | Operation is blocked with an actionable explanation. | REM-02, REM-03 |
| Timeout and duplicate restart request | Existing outcome is reconciled; cooldown and attempt limits are enforced. | REM-04, REM-05 |
| Jira code proposal | Ticket-linked diff includes commit context, rationale and accurate test status. | COD-01, COD-02, COD-05 |
| Stale patch or unauthorized repository write | Proposal is refreshed or blocked; no unauthorized push or merge occurs. | COD-04 |
| Admin with limited source access | Overview contains only authorized evidence and clearly identifies coverage limitations. | ADM-04, ADM-05 |

Additional decisions before technical design: supported workload kinds and environments; restart safety thresholds, cooldowns and verification windows; Jira/repository mapping; permitted code-validation environment; draft PR authorization; and issue-correlation rules. Approval-gated operational writes are now in scope; unrestricted autonomous remediation remains excluded.


## 19 Post change verification and business acceptance

A code or operational change shall remain open until its required verification gates pass. Planning a change, generating a patch, merging code, deploying code, restarting the application and verifying outcomes are separate events. Verification shall be tied to the change actually deployed; generating a proposal alone shall not start post-deployment verification.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| VER-01 | Build a reviewable verification plan from the applicable runbook, Jira acceptance criteria, recent change logs and deployment history. Identify expected technical outcomes, affected services, required restart behavior, observation window and business impact. Resolve conflicting or stale instructions before execution. | The plan references exact source versions and states measurable success and failure criteria. |
| VER-02 | Capture relevant pre-change health, logs, metrics and behavior as a baseline. Correlate the deployed artifact or commit, environment, service, deployment time and any restart with the verification execution. | Evidence identifies the actual running version; unrelated deployments and old logs cannot satisfy the checks. |
| VER-03 | After confirmed deployment and any required restart, poll authorized logs, metrics and health endpoints at configured intervals. Apply an initial readiness period, bounded observation window, source rate limits, cancellation and controlled retries. | Polling stops on completion, timeout or cancellation; missing or delayed telemetry is surfaced. |
| VER-04 | Check positive expected behavior and relevant regression signals, including startup readiness and runbook-specific log events where applicable. Absence of errors or a successful restart alone shall not establish technical success. | A healthy process with a missing expected outcome fails or remains inconclusive under the plan. |
| VER-05 | Record technical verification as pending, running, passed, failed or inconclusive, with timestamped evidence and criteria results. Distinguish application failures from inaccessible or insufficient telemetry. | Reviewers can reproduce the conclusion from recorded checks; telemetry access failure cannot produce a pass. |
| VER-06 | Classify whether a change affects business functionality during planning. Require business validation for affected functionality; uncertain classification shall await human review rather than bypass the gate. | A functionality change cannot be closed using technical verification alone. |
| VER-07 | Obtain manual validation from an authorized business reviewer against documented business acceptance criteria. Record reviewer identity, tested scenarios, result, comments, time and affected deployed version. | The reviewer explicitly approves or rejects; an agent cannot submit business approval on the reviewer's behalf. |
| VER-08 | Track business validation as not required, pending, approved or rejected. Record the authorized rationale for not required. Bind approval to the deployed version and agreed validation scope; relevant subsequent changes invalidate it. | A new behavior-changing deployment requires renewed validation. |
| VER-09 | Mark the change verified and permit closure only after technical verification passes and business validation is approved or validly not required. Jira closure or other source updates require the existing connector authorization and action policy. | Pending, failed, inconclusive or rejected gates prevent automatic successful closure. |
| VER-10 | Escalate failed or inconclusive technical checks, business rejection and overdue business validation to the designated owner. Propose rollback or remediation under the approved policy; any rollback requires its own authorization and subsequent verification. | A failure does not trigger an unapproved rollback or restart loop. |
| VER-11 | Persist verification progress and resume safely after interruption. Display the plan, baseline, polling progress, evidence, technical outcome and business gate in the admin issue overview. | Restarting the platform preserves pending business validation and does not duplicate source actions. |
| VER-12 | Revalidate user and source access during continued or resumed verification. Stop unauthorized polling and restrict evidence visibility under current policy. | Revoked access cannot continue retrieving protected logs or reveal them through the admin view. |

Additional acceptance scenarios:

| Scenario | Required result | Principal requirements |
| --- | --- | --- |
| Technical fix deployed and restarted | Polling verifies planned positive behavior against the deployed version and records supporting logs and health evidence. | VER-01 through VER-05 |
| Application ready but expected behavior absent | Change is failed or inconclusive rather than passed solely because startup succeeded. | VER-04, VER-05 |
| Logs delayed or unavailable | Bounded polling identifies the evidence gap and prevents unsupported technical success. | VER-03, VER-05 |
| Business functionality change | Technical pass leaves change awaiting explicit business acceptance. | VER-06 through VER-09 |
| Business rejection or pending review | Change remains open; designated owner receives escalation and proposed next steps. | VER-09, VER-10 |
| New version deployed after business approval | Relevant prior approval is invalidated and required checks are repeated. | VER-02, VER-08 |
| Platform restart during verification | Execution resumes safely with business review and polling state preserved. | VER-11 |

Additional decisions before technical design: log polling intervals and observation windows by change type; positive technical signals and regression thresholds; business-impact classification ownership; business approver roles, separation of duties and review deadlines; approval invalidation rules; and Jira closure and rollback policies. Numerical values remain configurable proposals until agreed.

## 20 Long running background tasks and agent loops

Background tasks continue independently of an open browser, while remaining bound to an authenticated initiating subject and project. Browser disconnection is not cancellation, and session independence is not indefinite delegated access. Agent loops perform bounded reasoning and tool use; scheduled polling and durable wake-ups are platform services.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| BKG-01 | Accept authorized work asynchronously and return a task/execution identifier and status location without waiting for completion. | Closing the initiating browser does not cancel accepted work; a later authorized request retrieves its status. |
| BKG-02 | Persist accepted durable work, checkpoints, pending waits and final outcomes; recover without repeating completed side effects. | Process failure before or after a checkpoint resumes or reconciles work safely. |
| BKG-03 | Support approval, authentication and business-review waits plus scheduled wake-ups without keeping a request or worker thread open. | Waiting work releases execution capacity and survives recovery. |
| BKG-04 | Bound loop steps, active processing time, absolute deadline, tokens, cost and retries. Define whether waits consume each budget; record cumulative usage across resumes. | Recovery or repeated waits cannot reset budgets or create unlimited execution. |
| BKG-05 | Provide authorized status, progress, last checkpoint, next wake-up, blockers and cancellation controls. Include these in the admin view. | Unauthorized users cannot inspect or cancel another project's work. |
| BKG-06 | Recheck subject, project and source access at resumed steps and before protected actions. Expired delegated access pauses for reauthentication; revocation blocks continued source access. | No shared-account fallback or new action under revoked access; logout handling follows explicit policy. |
| BKG-07 | Prevent concurrent duplicate execution using task ownership, leases or equivalent fencing plus idempotent effects and reconciliation. | Worker failure and lease expiry cannot permit stale workers to perform duplicate writes. |
| BKG-08 | Use bounded queues, concurrency limits, fair scheduling, retry backoff and dead-letter or manual-review handling for repeatedly failing tasks. | Overload is controlled and poisoned work cannot retry indefinitely. |
| BKG-09 | Distinguish interactive background work from scheduled unattended operations. Recurring tasks require separately approved identity, schedule, scope and lifecycle policies. | A background task cannot implicitly become an unattended recurring service. |

Additional acceptance scenarios: browser closure and reconnect; restart during a wait; budget exhaustion across resumes; duplicate worker recovery; cancellation during an in-flight action; expired/revoked credentials at wake-up; and overload with bounded admission.

Open policies: maximum elapsed duration and retention; logout versus cancellation; wait expiry and notification channels; lease/heartbeat/fencing configuration; workload fairness and concurrency; and which recurring tasks, if any, are approved.

## 21 Messaging channels

Support WhatsApp, Telegram and SMS as replaceable channel adapters for interactive requests. External transport providers need not be open source; their terms, cost, availability and data handling require separate evaluation. Messaging capabilities are production requirements, delivered after the core workflow.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| CHN-01 | Allow a linked authorized user to start an investigation, request status and cancel permitted work through each supported channel. | Each channel creates a traceable execution using the same workflow and policy as the browser. |
| CHN-02 | Link a channel account to an authenticated application identity using a secure, expiring enrollment flow. Do not treat phone number, display name or message text alone as authenticated application identity. Support unlinking and re-enrollment. | Unlinked, spoofed or revoked accounts cannot start protected work. |
| CHN-03 | Validate incoming webhook authenticity using the provider's supported mechanism; prevent duplicate processing, replay and abuse. | Duplicate/retried messages create at most one intended execution; forged events are rejected. |
| CHN-04 | Route messages through the same trusted identity, project and delegated connector checks. If fresh login, source consent or MFA is needed, send an expiring authenticated browser handoff rather than collecting credentials in chat. | Messaging cannot bypass OBO, MFA or project policy. |
| CHN-05 | Minimize outgoing content and apply channel-specific data classification, recipient and conversation policy. Default to redacted summaries and authenticated result links. Exclude secrets and sensitive source excerpts unless explicitly allowed. | No unauthorized data reaches a chat, group or recycled phone number. |
| CHN-06 | Support acknowledgement with execution ID, status requests and configured completion/failure notifications. Handle delivery failures, message-size limits and provider rate limits without duplicating business work. | Notification failures do not restart an investigation or mark it failed when its execution succeeded. |
| CHN-07 | Keep protected writes and business acceptance in an authenticated approval/review interface by default. Any future chat-native approval requires separately approved assurance and binding to exact action/version. | A text reply such as yes cannot approve a restart or business change under the default policy. |
| CHN-08 | Audit channel, verified sender mapping, event ID, execution and delivery outcome with redacted payloads. Define message retention, unlinking and account-reassignment behavior. | Operators can trace a request while minimizing provider-side and application-side sensitive content. |

Acceptance includes all three channels, duplicate webhook events, failed delivery, unlinked sender, revoked identity, denied project, required reauthentication and safe protected-action handoff. Open decisions include providers, account ownership, group-chat policy, outbound consent, retention and permissible notification content.

## 22 Browser web application

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| WEB-01 | Provide an authenticated browser web application as the primary interactive interface, with responsive desktop and mobile layouts. | Users can complete the investigation workflow in supported browsers without direct API commands. |
| WEB-02 | Provide project selection, investigation submission, task list, background progress, evidence/results and authorized cancellation. Preserve server-side work across navigation or browser closure. | Returning users can locate their execution and see current status without triggering duplicate work. |
| WEB-03 | Provide exact-action approval and manual business-validation screens with current permissions, version binding and expiry checks. | Unauthorized or stale approval/review submissions fail; business acceptance requires an explicit human decision. |
| WEB-04 | Provide an admin issue overview with authorized impact, timelines, agent tasks, source evidence, proposals and verification gates. | The overview respects project/source restrictions and clearly marks missing or stale evidence. |
| WEB-05 | Handle login, logout, MFA, session expiry, consent and source reauthentication; support secure deep links from messaging channels. | Redirects cannot bypass access checks or disclose protected results before authentication. |
| WEB-06 | Use accessible navigation/forms, keyboard operation and clear loading, failure, empty and partial-result states. Render source/model text safely and protect browser sessions against injection and CSRF as applicable. | Accessibility and security checks cover primary flows; retrieved text cannot execute script. |

Define supported browsers and accessibility target before acceptance. Frontend technology remains replaceable. Phase 1 includes a minimal functioning browser investigation interface; approval and admin/business features arrive with their corresponding workflow phases.

## 23 AKS hosting and container images

AKS is the required target hosting platform. Local Compose/kind remains the development environment; no actual AKS deployment is authorized by these requirements.

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| AKS-01 | Package UI, API and durable workers as independently deployable workloads, using versioned manifests/charts and environment configuration. | UI/API can scale and release separately; worker recovery preserves accepted tasks. |
| AKS-02 | Build all custom container images from approved maintained base images. Pin deployed image digests; record provenance, SBOM, scan results and rebuild policy for base-image updates. | A release identifies its base/build/runtime digests and passes agreed security gates. |
| AKS-03 | Apply least privilege, non-root execution, resource requests/limits, probes, graceful termination and appropriate disruption policies. | Rolling update, node drain and failed readiness checks preserve agreed service behavior. |
| AKS-04 | Route browser and API traffic through approved TLS ingress/gateway and internal Services. Expose only required routes; restrict external channel webhooks separately. | Internal administration, workers and data stores are unreachable through public application routes. |
| AKS-05 | Use scoped workload identities for approved Azure infrastructure access while preserving delegated-user identity for source operations. | Managed workload access cannot silently replace user OBO. |
| AKS-06 | Define persistent storage, backups, restore, network policy, secrets management and availability for all stateful dependencies. | Application pod replacement does not lose business state; recovery drills meet agreed targets. |

Confirm subscription, cluster, region, namespace, network exposure, registry, approved base-image catalog, node capacity/GPU needs and operational ownership before deployment. Framework-specific base images may differ; a single identical image for Java and UI is not required.
