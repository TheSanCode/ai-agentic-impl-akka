# Replace Ollama with an IT-approved embedding provider

Status: integration guide only. No company provider has been selected or implemented.

The current machine cannot download the Ollama model because company network policy blocks the registry. Use an approved provider rather than bypassing that restriction.

## 1. Obtain the approved integration details

Ask IT for the following through approved internal channels:

| Detail | Why it is needed |
| --- | --- |
| Model/deployment name and version policy | Identify the exact approved model and detect model changes. |
| Text-embedding capability | Retrieval needs vectors. A chat-only model is not a direct replacement. |
| API specification or approved SDK | Establish the request/response format; do not assume OpenAI or Ollama compatibility. |
| Embedding dimensions and input limits | Configure vector validation and compatible indexing, chunking and queries. |
| Authentication method | Determine whether to use workload identity, delegated tokens or an approved secret mechanism. |
| Approved endpoint and network/TLS requirements | Configure connectivity through approved routes and trust stores. |
| Rate limits, quotas, timeouts and cost constraints | Bound requests, concurrency and any retries. |
| Data-use approval | Confirm synthetic development text is permitted, including retention, residency and licensing requirements. |

Do not paste credentials, tokens, confidential endpoint URLs or internal documentation into assistant messages or conversation records. Keep runtime values in approved local configuration or a secret store, not committed files.

The repository requires open-source components where specified. A company recommendation does not automatically approve a licensing exception, hosted data transfer, enterprise-source use or production use; resolve these decisions explicitly.

## 2. Understand the current implementation

| Repository surface | Current behavior |
| --- | --- |
| [`EmbeddingGateway`](../src/main/java/io/agenticawithakka/application/ports/EmbeddingGateway.java) | Replaceable port exposing `capabilities()` and asynchronous `embed(String)`. |
| [`EmbeddingVector`](../src/main/java/io/agenticawithakka/application/ports/EmbeddingVector.java) | Model-identified vector contract; preserve its validation. |
| [`OllamaEmbeddingGateway`](../src/main/java/io/agenticawithakka/retrieval/OllamaEmbeddingGateway.java) | Calls Ollama-specific `/api/tags`, `/api/show` and `/api/embeddings`; verifies a local model digest and embedding capability. |
| [`SyntheticRetrievalProperties`](../src/main/java/io/agenticawithakka/retrieval/SyntheticRetrievalProperties.java) | Restricts the model to `nomic-embed-text:latest` and validates local-provider settings. |
| [`SyntheticPostgresConfiguration`](../src/main/java/io/agenticawithakka/config/SyntheticPostgresConfiguration.java) | Directly constructs the Ollama adapter for the opt-in profile. |
| [`application-synthetic-postgres.yaml`](../src/main/resources/application-synthetic-postgres.yaml) | Configures local Ollama, the pinned model digest and 768 dimensions. |

**Changing only the URL or model name is not sufficient.** A company API may use different authentication, model metadata and payloads, and the current property validation and wiring are Ollama-specific.

The default application remains database-free with mock retrieval. This replacement concerns the opt-in synthetic retrieval profile, not chat agents or production deployment.

## 3. Implement the approved adapter

After the provider and usage are approved:

1. Add a provider adapter implementing `EmbeddingGateway`, keeping vendor APIs and authentication outside application/domain contracts.
2. Use the documented embedding API. Return an `EmbeddingVector` with a stable provider/model/version identity and validated, finite values of the expected dimension. Use the same model and preprocessing for document and query embeddings.
3. Replace Ollama-specific model/digest verification with provider-appropriate identity/version verification. If deployment aliases can change, define an explicit version-change and reindex policy; do not claim an immutable model without evidence.
4. Keep connect/request deadlines and concurrency bounded. Respect throttling and retry limits. Report dependency/authentication failures explicitly without logging credentials, input text or full provider error bodies. Do not fall back to another model or fixed vectors.
5. Add validated provider-specific configuration and wire exactly the selected adapter in `SyntheticPostgresConfiguration`. Preserve the existing default mock behavior and fail startup on missing or invalid required configuration.
6. Add a dependency only if the approved API needs it and compatibility has been verified. An HTTP API does not automatically require a vendor SDK.

Endpoint, authentication and model configuration names must be designed with the selected provider. This guide does not introduce environment variables or a working provider-selection switch.

## 4. Preserve retrieval and database safety

- Preserve project/source authorization, ingestion checks, evidence reauthorization and citation provenance.
- Retain model identity and dimension filtering in stored chunks and queries. Equal dimensions do not make embeddings from different models compatible.
- Re-embed synthetic documents when changing models; verify dimension-dependent index/query configuration before using new vectors.
- Preserve the exact synthetic database name/schema and positive ownership checks before migration or restart-reset. Never reset an arbitrary or production database to accommodate a model change.
- Keep the profile synthetic-only. Approval to use a model is not approval to send user, ticket, source-code or enterprise evidence to it.

See [decision 0008](decisions/0008-synthetic-postgres-hybrid-retrieval.md) for the existing storage and reset boundary.

## 5. Validate before switching the local profile

Add focused adapter tests for the approved payload/authentication contract, model identity, wrong dimensions, non-finite/malformed vectors, unauthorized responses, throttling and timeouts. Use local fixtures for deterministic error coverage, not as evidence that the real model works.

With separate approval to call the company service, verify real embeddings using synthetic text. Update the existing Ollama-specific live/profile tests to exercise the chosen adapter without depending on the blocked registry. Then run database-backed ingestion, hybrid retrieval, provenance, cross-project denial, membership revocation, wrong-owner refusal and valid-owner restart-reset coverage.

Use the repository Maven Wrapper with JDK 25. Select tests that exist after implementation; the current `OllamaEmbeddingGatewayLiveTest` is not a company-provider test. Check actual test counts and report skips as unverified, not passed.

Record the chosen provider boundary and actual results in decision 0008 and affected setup/design documentation. Update roadmap checkboxes only when their required acceptance evidence passes. A successful API request alone does not complete P1-07 or full-lab acceptance.

## Current blocker

IT's approved embedding model/API and usage boundaries have not been supplied. No replacement adapter, new credentials, provider dependency or runtime configuration has been added.

Return to [local prerequisites](local-prerequisites.md) for current machine status.
