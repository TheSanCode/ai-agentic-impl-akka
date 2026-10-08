# Tool registry and permission checks (P1-06, tool part)

Date: 8 October 2026. Branch `feature/agentic`. Scope: the controlled tool registry, the policy-gated invocation path and the three Phase 1 read tools. No skill registry, agents, policy or identity adapters, HTTP endpoints, Spring wiring or new dependencies were added.

Sources: [Phase 1 instructions §3, §6–7](../implementation/phase-1-instructions.md), [technical design §4](../design/technical-design.md), requirements SCP-03, AGT-06, SKL-01, TOL-01..05 and DEL-04/DEL-06 in the [production requirements](../requirements/agenticawithakka-production-requirements.md), and [decision 0003](0003-domain-contracts-and-ports.md).

## Decisions

- **Package.** `io.agenticawithakka.tools` is framework-free and reaches sources only through application ports. `ArchitectureBoundaryTest` now covers it too.
- **Tool metadata (TOL-01).** `ToolDefinition` declares the versioned `ToolRef`, description, `ToolRisk` (`READ`/`DRAFT`/`WRITE`), permitted agent roles, argument schema (`ArgumentSpec`), optional `TimeWindow`, timeout (at most 2 min), retries (at most 3) and output limits (at most 50 passages and 100,000 characters). Invalid metadata fails at construction.
- **Read-only installation (SCP-03).** `ToolRegistry` refuses to register a tool whose risk is not enabled. `PhaseOneReadTools` enables only `READ` and registers exactly `searchKnowledge`, `queryMockLogs` and `inspectMockHealth`, all version 1 and Investigation-only. Duplicate tool IDs are rejected.
- **Discovery (SKL-01 for tools).** Tools are filtered by agent role, then by a `DISCOVER_TOOL` policy decision per tool. A policy failure hides the tool (fail closed).
- **Invocation order.** No adapter or delegated-token call happens until every check passes:
  1. The request's execution and task IDs match the trusted `ToolInvocationContext`, which is built from the `TaskEnvelope`.
  2. The deadline has not passed.
  3. The tool ID and version are registered; otherwise the outcome is `UNKNOWN_TOOL`.
  4. The agent role is permitted.
  5. Arguments match the schema (AGT-06). Unknown keys such as `userId` or `projectId` are rejected, so model output cannot change identity or project (DEL-06). Time windows are bounded.
  6. Policy permits `INVOKE_TOOL` for `tool:<id>:<version>`, and `READ_EVIDENCE` for each resource argument as `arg:<name>:<value>` (TOL-02). A policy failure returns `UNAVAILABLE`.
- **Execution.** The timeout is the smaller of the tool timeout and the remaining deadline. Only `DEPENDENCY_UNAVAILABLE` is retried, immediately and up to `maxRetries`. `PortException` codes map to structured `ToolOutcome` statuses (TOL-03). Other exceptions become `FAILED` without their message.
- **Output release (TOL-05).** These checks run before any content is returned:
  - Any passage from another project discards the whole result.
  - Each distinct source ID is re-authorized with `READ_EVIDENCE`, and denied passages are dropped silently.
  - An empty result is reported as `NOT_FOUND`.
  - Whole passages are kept up to the passage and character limits, and `truncated` is set when any are dropped. If even the first passage is too large, the outcome is `FAILED` (`output.tooLarge`).
  - Adapter results larger than 500 passages are rejected.
- **Delegated access (DEL-04).** The source tools request credentials for the trusted identity and project with a configured `DelegationTarget` (audience and scopes). A missing or expired credential gives `AUTHENTICATION_REQUIRED`; a wrong audience or missing scope gives `DENIED`. There is no shared-credential fallback. The secret never appears in outcomes.
- **Lab limits (provisional).**

  | Tool | Timeout | Retries | Passages | Characters | Other |
  | --- | --- | --- | --- | --- | --- |
  | `searchKnowledge` | 10 s | 1 | 10 | 30,000 | Query up to 500 characters; 1–10 results, default 5 |
  | `queryMockLogs` | 10 s | 1 | 20 | 20,000 | Window up to 6 h; lookback 30 days |
  | `inspectMockHealth` | 5 s | 1 | 5 | 5,000 | — |

## Verification

From the repository root with Temurin 25 selected, as in [backend scaffold](../implementation/backend-scaffold.md), I ran `.\mvnw.cmd -B -ntp clean verify`. Result on 8 October 2026: **114 tests, 0 failures/errors/skips, BUILD SUCCESS**. That is the 59 previous tests plus 55 new ones:

| Test | Count | Covers |
| --- | --- | --- |
| `ToolDefinitionTest` | 24 | Rejects forged identity or project keys, missing or malformed references, path-like values, bad instants, reversed, too-wide, too-old or future windows, oversized text and out-of-range integers, without echoing values. Rejects invalid metadata. |
| `ToolRegistryTest` | 21 | Draft or write tools and duplicates rejected. Discovery scoped by role and policy, and fails closed. Unknown tool or version, forbidden role, invalid arguments, context mismatch, expired deadline, tool or resource denial and policy outage all stop before execution. Covers output bounds, `NOT_FOUND`, cross-project discard, per-source re-authorization, timeout, error mapping, bounded retries and no leaked exception messages. |
| `ReadToolsTest` | 9 | Exactly the three read tools are discoverable by Investigation and none by Coordinator. `restartPod` is unknown. Search, logs and health use only the trusted context. Delegation is bound to identity, project and audience. Expired, missing, wrong-audience and narrower-scope credentials stop before the source call. The secret is absent from outcomes. |
| `ArchitectureBoundaryTest` | +1 | `tools` references no Akka, Spring, servlet, Jackson or adapter-package types. |

Mutation check: when the invocation policy gate was bypassed, 4 tests failed, in `ToolRegistryTest` and `ReadToolsTest`. The source was then restored. The packaged-JAR health smoke check was not rerun because the HTTP and configuration code did not change.

## Not done and open decisions

- **Skill registry.** It is not implemented: no skill definitions, owners, dependencies, selective loading or nested-delegation limits (SKL-01..03). P1-06 stays open for that part.
- **Real adapters.** Policy, identity and delegated-token behaviour is verified only with test fakes. Real adapters arrive in P1-04 (mock connectors and OIDC) and P1-07 (search). Composition wiring into Spring and the agent runtime is deferred to P1-08 so no unsatisfied beans are introduced.
- **Not implemented:**
  - Audit records of tool calls and policy decisions (AUD-01, P1-09 tracing).
  - Per-tool concurrency and rate limits, and step or token budget accounting (P1-08).
  - Propagating cancellation to adapters; a timed-out call is abandoned, not interrupted.
- **Open decisions:**
  - The lab limits above, until model selection in P1-10.
  - Whether resource arguments and evidence sources should map to `permissionRef` rather than IDs in the real policy adapter.
  - Whether to tell the agent that some evidence was withheld. Currently it is dropped silently to avoid revealing that restricted content exists.
  - Retry back-off.
- **Ordering.** As with P1-05, this work came ahead of P1-03 and P1-04 because it depends only on ports.
