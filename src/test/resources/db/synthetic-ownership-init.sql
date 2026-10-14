CREATE EXTENSION vector;
CREATE SCHEMA agentica_synthetic;
CREATE TABLE agentica_synthetic.database_ownership (
    application_id text PRIMARY KEY CHECK (application_id = 'agenticawithakka'),
    owner_id uuid NOT NULL,
    data_classification text NOT NULL CHECK (data_classification = 'synthetic-only'),
    database_name text NOT NULL CHECK (database_name = 'agenticawithakka_synthetic')
);
INSERT INTO agentica_synthetic.database_ownership
    (application_id, owner_id, data_classification, database_name)
VALUES
    ('agenticawithakka', '151d4fa3-43a7-4821-a4ae-16513812569e', 'synthetic-only',
     'agenticawithakka_synthetic');
