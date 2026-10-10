# Decision 0008: Synthetic PostgreSQL hybrid retrieval foundation

Date: 10 October 2026

Status: Approved for isolated synthetic-data development only; application activation and production use are not approved

## Decision

Use PostgreSQL full-text search and pgvector cosine similarity behind the existing retrieval ports for the next synthetic-only implementation slice. Combine keyword and semantic candidate ranks with reciprocal-rank fusion (constant 60). Store text chunks with project, source/version, classification, observation time, permission reference, optional source link, embedding model identifier and vector dimension.

The user selected PostgreSQL full-text plus pgvector, with production use deferred pending separate approval. The user also selected ephemeral synthetic data reset on process restart and excluded user or enterprise source content. Accordingly:

- Ingestion is an explicitly invoked local synthetic operation. The caller must have current project/source access and an affirmative `READ_EVIDENCE` policy decision before embedding, and access is checked again before writes and deletes.
- Search filters by project, current permitted source IDs, exact embedding model identifier and vector dimension in SQL. Each returned passage is filtered and reauthorized again before release.
- A source version can be replaced or deleted independently; citations preserve source ID/version, project, classification, observation time, permission reference and optional safe HTTP(S) link.
- Tests use a disposable PostgreSQL container with pgvector image `pgvector/pgvector:0.8.7-pg17` pinned by multi-platform digest `sha256:ac08538c6f8b9904c33c8224c5e5706dbe760aca29db1d096972b4052c22a75d`. The container is destroyed by Testcontainers after the test JVM exits.
- The index adapter is not wired into the application; the default remains `MockSearchGateway`. No standalone local database or application startup reset routine was added. Thus **reset-on-application-restart is a selected requirement, not yet implemented or verified for an active application database**. When this adapter is wired, activation must be restricted to an isolated synthetic-only local profile whose database ownership and reset target are positively verified; it must never clear an arbitrary or production database.
- No embedding model/provider was selected. Adapter tests use fixed synthetic vectors only. A query is matched only against the same model identifier and vector dimension used at ingestion; no model quality or semantic relevance claim is made.
- The migration currently uses exact vector-distance scans, not an approximate-nearest-neighbor index. Performance suitability is unverified.

PostgreSQL and the pgvector extension provide a local development/test foundation only. Production storage, retention, backup, encryption, residency, multi-instance revocation, capacity and provider/data-use decisions remain separate gates.

## Verification evidence

On Temurin 25, the following focused command passed **5 PostgreSQL/pgvector integration tests** against PostgreSQL 17 and the pinned pgvector image:

```powershell
.\mvnw.cmd -B -ntp '-Dtest=PostgresKnowledgeIndexTest' test
```

The integration tests exercise migration, lexical/vector hybrid results, project/source/model filtering, source-link provenance, versioned upsert/delete, authorized Alpha/Beta retrieval, rejected cross-project ingestion and membership revocation during embedding. The database is Testcontainers-managed and contains only synthetic test fixtures.

Testcontainers 2.0.5 is used because Testcontainers 1.x's Docker client sent API 1.32, which this environment's Docker Engine 29 rejects (minimum API 1.40). The selected 2.x artifact coordinates and PostgreSQL module API were verified against the official 2.0.5 release. No standalone database was provisioned.
