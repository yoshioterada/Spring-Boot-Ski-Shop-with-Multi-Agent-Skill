CREATE TABLE oauth_scopes (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_oauth_scopes PRIMARY KEY (id),
    CONSTRAINT uq_oauth_scopes_name UNIQUE (name)
);

INSERT INTO oauth_scopes (name, description, is_default) VALUES
    ('read', '読み取り権限', TRUE),
    ('write', '書き込み権限', FALSE),
    ('admin', '管理者権限', FALSE);
