# AgenticaWithAkka Technology Stack

Version 0.2 | 7 October 2026 | Recommended baseline pending compatibility tests

## Selection policy

Use the following candidates for the local implementation. These are design selections, not verified dependency combinations or a production deployment approval. Pin exact patches, image digests and model versions during bootstrap. Keep domain contracts independent of providers.

## Recommended stack

| Area | Candidate | Why and phase |
| --- | --- | --- |
| Browser UI | Angular 22, TypeScript, Angular Material | Responsive investigation screens in Phase 1; approvals, business review and admin screens later. Resolve compatible Node/TypeScript versions from Angular's matrix. |
| Java | OpenJDK 25 LTS baseline | Stable baseline. Latest-GA Java remains a separate compatibility experiment; the request for newest Java is not considered fulfilled by merely choosing LTS. |
| Backend | Spring Boot 4.0.x plus Spring Security | Initially align with Spring AI 2.0.x and verify latest supported patch. Boot 4.1.x is an upgrade candidate after combined tests. |
| AI integration | Spring AI 2.0.1 candidate | Chat, embeddings, tool calling and retrieval adapters; optional MCP boundary. |
| Actor runtime | Akka Typed | Two-agent local communication; Akka Persistence Typed in Phase 2. Exact runtime version and persistence plugin remain to select/test. |
| Local inference | Ollama | Local chat and embedding endpoints. Pick actual models after measuring GPU/RAM, tool reliability and weight licenses. |
| Identity | Keycloak and Spring Security OIDC | Browser login, MFA and local token-exchange proof. Real-source delegation stays source-specific. |
| Authorization | Application policy service initially; optional OPA adapter | Enforce user/project/source/tool boundaries in code. |
| Application state | PostgreSQL and Flyway | Durable records, migrations, projections and audit metadata. |
| Search/vector | PostgreSQL full-text plus pgvector | Initial hybrid search and vectors in one store. Optional OpenSearch adapter for search comparison. |
| Background execution | Akka orchestration plus PostgreSQL-backed durable work/wake-up records | Persist budgets/waits, dispatch safely and reconcile effects; actor timers alone are insufficient. Select one authoritative state model with a tested journal/plugin. |
| Polling and notifications | Deterministic scheduler and transactional outbox | Bounded verification checks, retries and delivery records independent of AI loop/model calls. |
| Telegram | Telegram Bot API adapter | Mock events first; validate webhook secret and account linking before real messages. |
| WhatsApp | Twilio WhatsApp adapter candidate | One provider option for sandbox integration; Meta direct adapter remains replaceable. |
| SMS | Twilio Programmable Messaging adapter candidate | Shares a provider option with WhatsApp. Validate sender provisioning, destination support and outbound policy. |
| Kubernetes actions | Fabric8 Kubernetes Java client and local kind cluster | Phase 4 exact-target inspection and approved restart/eviction. Validate client/cluster compatibility. |
| Jira/repository | Vendor REST adapters; JGit where local repository inspection is needed | Ticket/commit context in Phase 3. Never imply vendor delegation support without a proof. |
| Isolation | Disposable code-validation worker with resource limits and restricted egress | Phase 3 patch testing; no production secrets or privileged host socket. Sandbox implementation requires its own threat review. |
| Telemetry | OpenTelemetry, Prometheus, Grafana; Loki optional | Correlated execution, metrics and redacted logs. |
| Java tests | JUnit 5, AssertJ, Testcontainers and mock HTTP servers | Contracts, permissions, workflow states and infrastructure integration. Resolve library compatibility during setup. |
| Browser tests | Playwright plus accessibility checks | Browser journeys, reconnect, denied access and review screens. |
| Build/runtime | Maven Wrapper, npm lockfile, Docker Compose | Reproducible local startup; no cloud requirement for first slice. |
| CI | GitHub Actions | Build, tests, dependency/security checks and evaluation evidence; deployment gates remain separate. |

Do not add Kafka, Redis, a second vector store or a second durable workflow engine until a demonstrated requirement justifies them. Persistence tables and the Akka journal must not independently own the same state; choose ownership during the Phase 2 persistence decision.

## Java compatibility decision

Official documentation checked on 7 October 2026 lists Spring Boot 4.0.8 and 4.1.1 compatibility through Java 26; the 4.2.0-M2 preview lists Java 27. Do not claim Java 27 is supported by the stable baseline. If newest Java is mandatory immediately, prove the preview stack in an isolated experiment and record its tradeoffs, including Spring AI and Akka compatibility. The main lab retains a tested supported baseline.

## Open source and external services

Keycloak, Spring Security, PostgreSQL/pgvector and candidate OpenSearch fulfill the intended open-source component direction, subject to license inventory. Current Akka is BSL/source available; local development and production licensing differ. Model weights have their own licenses. WhatsApp, Telegram and SMS transports are external services, not an entirely open-source delivery stack. Twilio is a proposed paid service, not an account purchase or selected subscription.

## Setup and delivery boundaries

Phase 1 uses frontend, API, identity, PostgreSQL/vector storage, inference and mock sources. Phase 2 adds durable scheduling/recovery. Phase 3 adds ticket/code sandbox work; Phase 4 adds Kubernetes actions; Phase 5 adds technical/business verification; Phase 6 validates real messaging and source adapters.

Mock all messaging locally before configuring real accounts. Do not register providers, send messages or deploy based solely on this document. UI login and source consent/approvals remain distinct.

## Primary references

- [Angular versions](https://angular.dev/reference/versions)
- [Angular releases](https://angular.dev/reference/releases)
- [Spring Boot 4.0 requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html)
- [Spring Boot current requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot 4.2 preview requirements](https://docs.spring.io/spring-boot/4.2/system-requirements.html)
- [Spring AI](https://docs.spring.io/spring-ai/reference/)
- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Twilio messaging channels](https://www.twilio.com/docs/messaging/channels)
- [Akka licensing](https://akka.io/bsl-license-faq)

Remaining bootstrap decisions: exact versions and licenses; actual models and laptop resource budgets; persistence plugin/state ownership; channel provider/consent policy; and real-source delegated authentication feasibility.

## AKS and base image requirements

AKS is the target platform. Use separate UI and API Deployments/Services; add internal durable-worker deployment in Phase 2. Keep messaging adapters as API modules initially. Use approved multi-stage Node/static-server images for UI and JDK-builder/Java-runtime images for API/workers, pinned by digest. Exact corporate base-image names are not supplied and must be confirmed.

Recommended additions: approved ACR registry, versioned Helm charts, approved TLS gateway, Azure Workload Identity for infrastructure permissions, approved secrets store and network policies. UI/API image builds begin locally; AKS provisioning/deployment awaits explicit readiness and authorization. Read [technical design Section 19](technical-design.md) for routes, replicas, sessions, stateful hosting and channel callback access.

Do not assume a workload managed identity satisfies OBO or that all data/model services fit inside a single application pod. Confirm node/GPU budgets, durable storage and production dependency availability.
