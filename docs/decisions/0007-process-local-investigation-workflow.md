# Decision 0007: Process-local read-only investigation workflow

Date: 9 October 2026

Status: Implemented local demonstration; not a production workflow

## Decision

Compose the existing `TaskEnvelope`, `TaskResult`, `ToolRegistry`, identity context and policy/source ports into a deterministic in-process Coordinator and Investigation execution. The Coordinator runs a bounded fixed sequence: search synthetic knowledge, then query synthetic service logs. It returns authorized evidence references and passages only; it does not call Spring AI, infer causes, claim an action, or start Akka actors.

The authenticated API is:

| Method and path | Behavior |
| --- | --- |
| `POST /api/projects/{projectId}/investigations` | Authorize the requested project, return HTTP 202 and a server-generated execution ID. |
| `GET /api/executions/{executionId}` | Return current status and progress after subject and project checks. |
| `GET /api/executions/{executionId}/result` | Return the completed task result and cited passages after current project and per-source authorization. |
| `POST /api/executions/{executionId}/cancel` | Cancel work owned by the caller. A current in-flight read may finish, but no later tool step starts and its callback cannot overwrite the cancelled result. |
| `POST /api/executions/{executionId}/resume` | Resume an authentication-paused execution only as the same `(iss, sub)`, with current project access and within the original deadline and cumulative step budget. |

The API uses the existing OIDC resource-server filter and server-configured principal/project/source mappings. Project IDs in paths identify the requested target only; they do not grant membership. Tool calls use the policy-gated registry. A missing/expired identity context at a tool policy check is returned as `AwaitingAuthentication`. Results are rechecked using the trusted original principal key, current membership and current access to every evidence source. Membership revocation therefore blocks further work and result/citation reads.

The mock adapters seed fixed synthetic runbook, log and health passages for project IDs in configured principal grants. The identity's source grant and the mock source's independent ACL must both allow a read. An example local grant (the issuer and JWK URL must match the test/development identity provider) is:

```yaml
app:
  security:
    issuer-uri: https://identity.example.test/realms/local
    jwk-set-uri: https://identity.example.test/realms/local/protocol/openid-connect/certs
    audience: agenticawithakka-api
    principals:
      - issuer: https://identity.example.test/realms/local
        subject: local-user
        projects:
          alpha:
            resources: [service:billing]
            sources: [mock-knowledge, mock-logs, mock-health]
```

Credentials for real enterprise sources are neither required nor supported by these adapters. The mock signing key is randomly generated per process, used only for synthetic delegated credentials, and is not persisted or logged.

## Process-local limits and failure boundaries

Each execution is capped at four tool steps (including retries after reauthentication) and 45 seconds from initial admission. At most 32 executions are active, the executor has two workers and a queue of 32, and at most 500 execution records/results are retained. The 500-record cap has no eviction policy: once reached, new work is rejected until restart. Tool schemas also bound arguments, time windows, passage counts and text output. No model is called, so no token or model-cost budget is consumed.

State, authorization contexts and membership revocation are in memory on one application instance. A process restart loses accepted tasks, progress and results; another replica cannot see the same state or a revocation performed in this instance. There is no durable recovery, distributed queue, actor persistence, audit trail, or guarantee that cancellation interrupts a currently executing source request. Do not describe this as restart-safe background work or multi-instance revocation.

The process-local execution trace retains at most 10,000 lifecycle events and evicts the oldest event when full. Each event contains only the server-generated execution and correlation IDs, timestamp, event/status enums and step count. It excludes project/user/source identifiers, request text, evidence passages, credentials, free-form errors and model reasoning. Events are available only through the internal trace component; no trace API or log exporter is exposed. They disappear on restart and are operational metadata, not a durable audit record. Production retention, audit access and export policy remain open.

This API does not add protected writes, approvals, browser UI, durable persistence, audit logging or deployment. Spring AI/Akka compatibility probes do not mean this flow uses either runtime. Real-source OBO and production Akka licensing/runtime-key decisions remain open and independent.

## Verification evidence

Focused Temurin 25 verification command:

```powershell
.\mvnw.cmd -B -ntp '-Dtest=ProcessLocalInvestigationServiceTest,OidcResourceServerIntegrationTest,MockDelegatedSourceTest,ToolRegistryTest' test
```

The focused suite passed **35 tests**. It covers HTTP bearer-token creation/status/result and cross-project denial, another subject, tool execution, cancellation before execution and during an in-flight read, membership revocation during execution and after completion, authentication expiry/resume, and cumulative step exhaustion.

Execution tracing verification:

```powershell
.\mvnw.cmd -B -ntp '-Dtest=ProcessLocalInvestigationServiceTest,ExecutionTraceTest' test
```

The focused tracing suite passed **8 tests**. It verifies correlated lifecycle events across completion, reauthentication/resume and cancellation, confirms the trace representation excludes prompts, evidence text, credentials, project/user/source identifiers, and checks bounded oldest-event eviction. Traces remain process-local and are not exposed through an API.

Full Temurin 25 verification also passed:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

Result: **146 tests, zero failures, zero errors and zero skips; `BUILD SUCCESS`**. Changed Markdown relative links were checked and all targets exist. No deployment was performed.
