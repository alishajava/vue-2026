-- Postgres용 DDL. bytea는 H2의 VARBINARY와 달리 길이 제한이 없어서 length를 안 줘도 된다.
CREATE TABLE IF NOT EXISTS document_library (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    file_name VARCHAR(255),
    file_type VARCHAR(20),
    file_data BYTEA,
    registrant VARCHAR(255),
    registered_at TIMESTAMP,
    reference_month VARCHAR(7),
    updated_by VARCHAR(255),
    updated_at TIMESTAMP,
    hidden BOOLEAN NOT NULL DEFAULT FALSE
);
