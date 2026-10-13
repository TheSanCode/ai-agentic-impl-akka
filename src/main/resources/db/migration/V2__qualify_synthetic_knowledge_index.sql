ALTER TABLE knowledge_chunks
    ADD CONSTRAINT knowledge_chunks_synthetic_project_source_version_check
    CHECK (
        project_id ~ '^[a-z][a-z0-9-]{0,62}$'
        AND source_id ~ '^[A-Za-z0-9._:-]{1,128}$'
        AND source_version ~ '^[A-Za-z0-9._:-]{1,128}$'
        AND chunk_id ~ '^[A-Za-z0-9._:-]{1,128}$'
        AND permission_ref ~ '^[A-Za-z0-9._:-]{1,128}$'
    );
