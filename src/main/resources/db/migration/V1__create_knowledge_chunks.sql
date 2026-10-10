CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_chunks (
    project_id text NOT NULL,
    source_id text NOT NULL,
    source_version text NOT NULL,
    chunk_id text NOT NULL,
    classification text NOT NULL,
    observed_at timestamptz NOT NULL,
    permission_ref text NOT NULL,
    source_link text,
    content text NOT NULL,
    embedding_model_id text NOT NULL,
    embedding_dimensions integer NOT NULL,
    embedding vector NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    PRIMARY KEY (project_id, source_id, source_version, chunk_id, embedding_model_id),
    CHECK (embedding_dimensions = vector_dims(embedding))
);

CREATE INDEX knowledge_chunks_project_source_idx
    ON knowledge_chunks (project_id, source_id, embedding_model_id, embedding_dimensions);

CREATE INDEX knowledge_chunks_search_vector_idx
    ON knowledge_chunks USING gin (search_vector);
