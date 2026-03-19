CREATE TABLE oauth_clients (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    client_id VARCHAR(100) NOT NULL,
    client_secret VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oauth_clients PRIMARY KEY (id),
    CONSTRAINT uq_oauth_clients_client_id UNIQUE (client_id)
);

CREATE TABLE oauth_client_redirect_uris (
    client_id UUID NOT NULL,
    redirect_uri VARCHAR(500) NOT NULL,
    CONSTRAINT fk_oauth_client_redirect_uris FOREIGN KEY (client_id) REFERENCES oauth_clients(id) ON DELETE CASCADE
);

CREATE TABLE oauth_client_grant_types (
    client_id UUID NOT NULL,
    grant_type VARCHAR(50) NOT NULL,
    CONSTRAINT fk_oauth_client_grant_types FOREIGN KEY (client_id) REFERENCES oauth_clients(id) ON DELETE CASCADE
);

CREATE TABLE oauth_client_scopes (
    client_id UUID NOT NULL,
    scope VARCHAR(50) NOT NULL,
    CONSTRAINT fk_oauth_client_scopes FOREIGN KEY (client_id) REFERENCES oauth_clients(id) ON DELETE CASCADE
);
