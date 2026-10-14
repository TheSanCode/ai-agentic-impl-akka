# Decision 0008: Synthetic PostgreSQL hybrid retrieval

Date: 12 October 2026

Status: Implemented behind an opt-in synthetic-only profile; end-to-end database acceptance is blocked pending an available local PostgreSQL/pgvector engine. Production use remains unapproved.

## Decision

Use PostgreSQL full-text search and pgvector cosine similarity behind the existing retrieval ports. Combine keyword and semantic candidate ranks with reciprocal-rank fusion (constant 60). Store project, source/version, classification, observation time, permission reference, optional source link, embedding model identifier and vector dimension with each chunk.

For this synthetic-only profile, select the installed Ollama model `nomic-embed-text:latest`, digest `0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`, 768 dimensions. The adapter verifies that the model name/digest is present in Ollama's tags, checks its embedding capability, and requests actual embedding vectors. It fails startup on a missing/mismatched model and rejects a response with the wrong dimension. The model selection is not approval for model-driven agent execution, enterprise content, production use or quality claims.

The application wiring exists only in the explicitly activated `synthetic-postgres` profile. The default profile remains database-free and continues to use `MockSearchGateway`. The synthetic profile exposes one authenticated, fixed synthetic-runbook ingestion operation. The existing ingestion service requires current project/source permission and `READ_EVIDENCE` policy approval; search filters by project and currently permitted source IDs, model and dimension in SQL, then reauthorizes evidence before release. Citation fields preserve source/version, project, classification, observation time, permission reference and safe source link.

The synthetic PostgreSQL database must be provisioned explicitly using `infra/local/create-synthetic-database.sql`. It creates a new, exact-name database without dropping or replacing an existing database, requires a restricted pre-existing application role, creates the pgvector extension and an application ownership marker, and grants only the privileges required by the app role. The runtime refuses startup unless all of these checks pass before migrations or reset:

- JDBC URL is exactly a loopback PostgreSQL URL on a valid port and names `agenticawithakka_synthetic`.
- The connected catalog and configured schema match the expected application database and `agentica_synthetic`.
- The ownership table contains exactly one marker with application ID `agenticawithakka`, the configured owner UUID, `synthetic-only` classification and matching database name.

Flyway `clean` is disabled. After the marker is verified, startup applies migrations and deletes only rows from `agentica_synthetic.knowledge_chunks`. It never drops a database, schema, extension, ownership marker or unrelated table. A missing/mismatched marker or non-loopback target fails closed before either migration or reset. Application connections set `agentica_synthetic` as their schema.

The pgvector migration assumes the extension was provisioned by the local database setup, rather than granting the application startup process permission to install database extensions. The lower-level `PostgresKnowledgeIndexTest` uses fixed vectors solely for deterministic SQL-adapter coverage; it is not a substitute for the live-model profile test.

Production storage, retention, backup, encryption, residency, multi-instance revocation, capacity, embedding-model data-use/license review and operational ownership remain separate decisions. The vector search currently uses exact distance scans; performance suitability is unverified.

## Verification evidence and current blocker

On Temurin 25, the following command passed: 154 tests, 0 failures, 0 errors, 1 skipped. This included the live Ollama embedding test, all 3 ownership-guard tests, and the existing OIDC/authentication tests. The skipped test was the profile-level PostgreSQL integration test.

```powershell
.\mvnw.cmd -B -ntp clean verify '-Dtest=!PostgresKnowledgeIndexTest' '-Dollama.embedding.live=true'
```

The separate targeted run of `SyntheticPostgresProfileIntegrationTest` also ended successfully with **1 skipped** because Testcontainers could not find a Docker environment. Docker CLI is present, but the Docker Engine named pipe is unavailable; Podman and local PostgreSQL server/client tools are not installed. Therefore actual database-backed ingestion, hybrid retrieval, revocation and restart-reset have **not** been verified in this environment. The test code uses real Ollama embeddings and the digest-pinned disposable pgvector image `pgvector/pgvector:0.8.7-pg17@sha256:ac08538c6f8b9904c33c8224c5e5706dbe760aca29db1d096972b4052c22a75d`; no fixed vectors stand in for that acceptance test.

### Historical adapter evidence (not verified in this session)

An earlier run recorded **5 passing tests** for the deterministic SQL adapter against the same pinned pgvector image:

```powershell
.\mvnw.cmd -B -ntp '-Dtest=PostgresKnowledgeIndexTest' test
```

Those tests use fixed vectors for repeatable adapter assertions. They do not prove that this session's profile wiring, real-model ingestion, revocation path or restart-reset works. The current test fixture provisions the pgvector extension before applying migrations.
