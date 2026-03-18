CREATE TABLE login_attempts (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    is_success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    CONSTRAINT pk_login_attempts PRIMARY KEY (id)
);

CREATE INDEX idx_login_attempts_user_id ON login_attempts (user_id);
CREATE INDEX idx_login_attempts_timestamp ON login_attempts (timestamp);
