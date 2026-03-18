CREATE TABLE oauth_tokens (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    access_token TEXT NOT NULL,
    refresh_token VARCHAR(255),
    client_id VARCHAR(100),
    user_id UUID,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_oauth_tokens PRIMARY KEY (id),
    CONSTRAINT uq_oauth_tokens_access_token UNIQUE (access_token)
);

CREATE TABLE oauth_token_scopes (
    token_id UUID NOT NULL,
    scope VARCHAR(50) NOT NULL,
    CONSTRAINT fk_oauth_token_scopes FOREIGN KEY (token_id) REFERENCES oauth_tokens(id) ON DELETE CASCADE
);

CREATE INDEX idx_oauth_tokens_user_id ON oauth_tokens (user_id);
CREATE INDEX idx_oauth_tokens_refresh_token ON oauth_tokens (refresh_token) WHERE refresh_token IS NOT NULL;
