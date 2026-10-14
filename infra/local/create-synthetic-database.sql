\set ON_ERROR_STOP on
\getenv owner_id AGENTICA_SYNTHETIC_DB_OWNER_ID
\getenv app_role AGENTICA_SYNTHETIC_DB_USERNAME

SELECT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = :'app_role'
        AND NOT rolsuper
        AND NOT rolcreatedb
        AND NOT rolcreaterole
) AS synthetic_role_is_restricted \gset
\if :synthetic_role_is_restricted
\else
\echo 'The configured application role must exist and must not be a superuser or role/database administrator.'
\quit 3
\endif

SELECT 'CREATE DATABASE agenticawithakka_synthetic' \gexec
\connect agenticawithakka_synthetic

CREATE EXTENSION vector;
CREATE SCHEMA agentica_synthetic AUTHORIZATION CURRENT_USER;
CREATE TABLE agentica_synthetic.database_ownership (
    application_id text PRIMARY KEY CHECK (application_id = 'agenticawithakka'),
    owner_id uuid NOT NULL,
    data_classification text NOT NULL CHECK (data_classification = 'synthetic-only'),
    database_name text NOT NULL CHECK (database_name = 'agenticawithakka_synthetic')
);
INSERT INTO agentica_synthetic.database_ownership
    (application_id, owner_id, data_classification, database_name)
VALUES
    ('agenticawithakka', :'owner_id'::uuid, 'synthetic-only', 'agenticawithakka_synthetic');

GRANT CONNECT ON DATABASE agenticawithakka_synthetic TO :"app_role";
GRANT USAGE, CREATE ON SCHEMA agentica_synthetic TO :"app_role";
GRANT SELECT ON TABLE agentica_synthetic.database_ownership TO :"app_role";
