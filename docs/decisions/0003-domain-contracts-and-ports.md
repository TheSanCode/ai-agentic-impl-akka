# Domain contracts and replaceable ports (P1-05)

Date: 8 October 2026. Branch `feature/agentic`. Scope: versioned agent/task/tool/evidence contracts and Phase 1 port interfaces only. No adapters, agents, persistence, HTTP endpoints or new dependencies were added.

Sources: [technical design §3–5](../design/technical-design.md), [Phase 1 instructions §3–9](../implementation/phase-1-instructions.md), requirements AGT-02, AGT-06, ARC-01, MSG-06, SCP-04 and TOL-01 in the [production requirements](../requirements/agenticawithakka-production-requirements.md).

## Decisions

- **Framework-free contracts.** `io.agenticawithakka.domain.contracts` holds Java records and enums with no Spring, Akka, HTTP, Jackson or credential types. Constructors validate every field and throw `ContractViolationException`. Its message names the field and rule but never echoes the rejected value. Bean Validation annotations were not used, so the rules also apply outside Spring, for example inside actors or after deserialization.
- **Schema version 1.** `TaskEnvelope.SCHEMA_VERSION = 1`; any other version is rejected. A future version needs an explicit migration rule.
- **TaskEnvelope** carries the design's required fields: execution, task and correlation IDs, project, identity-context reference, deadline, agent role, input and reply route. It also adds a `budget` field covering steps, elapsed time, tokens and cost. That addition implements AGT-02. Budget values come from configuration, never from client or model input.
- **Identity references.** `IdentityContextRef` is an opaque, server-issued reference with a format constraint, and it contains no token material. `ReplyRoute` is a serializable routing identifier; actor references stay inside the runtime adapter.
- **TaskResult invariants:**
  - `SUCCEEDED` has no error code.
  - `PARTIAL` requires an error code and stated limitations.
  - `FAILED` requires a non-cancellation error code.
  - `CANCELLED` requires error code `CANCELLED`.
  - `FACT` findings must cite evidence.
  - Findings may cite only evidence that appears in the result's `evidenceRefs`.
  - All lists are bounded, null-free and immutable.
- **ExecutionStatus.** It covers the eight design states. Terminal states (`SUCCEEDED`, `FAILED`, `CANCELLED`) never transition again, so a late result cannot override a cancellation.
- **Tool requests.** `ToolRequest` uses a camelCase registered tool ID with a version, structurally bounded arguments, and an idempotency key. Per-tool argument schemas belong to the tool registry (P1-06).
- **Ports.** `io.agenticawithakka.application.ports` defines `AgentRuntime`, `ModelGateway`, `SearchGateway`, `SourceConnector`, `DelegatedTokenProvider` and `PolicyDecisionService`:
  - All I/O methods return `CompletionStage`, so actor dispatchers are not blocked.
  - Every port exposes `AdapterCapabilities`, which states what the adapter supports and what it doesn't.
  - Failures use `PortException` with a contract `ErrorCode`.
  - `DelegatedTokenProvider` must fail with `UNSUPPORTED`, `DENIED` or `AUTHENTICATION_REQUIRED` and never fall back to shared credentials.
  - `DelegatedCredential` is a non-record class with no bean getters, and its `toString` redacts the secret.

## Verification

Commands: from the repository root with Temurin 25 selected, as in [backend scaffold](../implementation/backend-scaffold.md), run `.\mvnw.cmd -B -ntp clean verify`. Result on 8 October 2026: **59 tests, 0 failures/errors/skips, BUILD SUCCESS**. These are the 9 existing scaffold tests plus 50 new ones:

| Test | Count | Covers |
| --- | --- | --- |
| `ContractValidationTest` | 42 | Malformed envelope, ID, input, budget, tool, argument, idempotency, evidence and result cases are rejected. Messages do not echo values. Argument maps are immutable copies. Terminal states cannot transition. |
| `ContractJsonTest` | 2 | Jackson 3 round-trip of `TaskEnvelope`. Deserialization rejects an unsupported schema version and an invalid project ID through the same constructors. |
| `PortContractTest` | 4 | Credential secret absent from `toString` and JSON. Policy denials need a stable reason code. Port requests are bounded. Port errors carry an error code. |
| `ArchitectureBoundaryTest` | 2 | `domain` and `application/ports` reference no Akka, Spring, servlet, Jackson or adapter-package types. A temporarily injected `org.springframework` reference made it fail as expected; the change was then reverted. |

The packaged-JAR health smoke check was not rerun because the HTTP and configuration code did not change.

## Not done and open decisions

- Not done: `ActionProposal` and `VerificationPlan` contracts and the `ActionExecutor`, `ApprovalRepository`, `ExecutionRepository` and `VerificationScheduler` ports. Phase 1 does not implement approval or verification workflows, so the Phase 0 item "Define message, tool and verification contracts" stays open.
- Open decision: the cost unit for `Budget.maxCostMicros`, a currency or internal unit. Zero means no paid usage, which suits local models.
- Open decision: concrete budget values and length limits are lab proposals pending the model selection in P1-10.
- Not done: JSON field shapes are not yet a published API. HTTP DTOs (P1-09) may differ from internal contracts, and mapping must preserve validation.
- Ordering: P1-05 was done before P1-03 (local infrastructure) and P1-04 (identity), because the contracts depend on neither. Identity-reference creation and policy enforcement are interfaces only until P1-04.
